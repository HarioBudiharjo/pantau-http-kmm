package com.pantauhttp

import com.pantauhttp.internal.platform.NotificationService
import com.pantauhttp.internal.platform.PlatformCapture
import com.pantauhttp.internal.platform.ShakeDetector
import com.pantauhttp.internal.platform.createInternalHttpClient
import com.pantauhttp.internal.platform.runOnMainThread
import com.pantauhttp.internal.remote.DashboardPusher
import com.pantauhttp.internal.remote.currentDeviceIdentity
import com.pantauhttp.internal.store.TransactionRecorder
import com.pantauhttp.internal.store.TransactionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Headless entry point: capture, storage, exports and dashboard push without any UI.
 * Apps that also want the inspector screens use `PantauHttp` from the `pantau-http` module.
 */
public object PantauHttpCore {

    private class State(
        val configuration: PantauHttpConfiguration = PantauHttpConfiguration(),
        val isStarted: Boolean = false,
    )

    private val state = MutableStateFlow(State())
    private val pusherLock = Mutex()
    private var pusher: DashboardPusher? = null

    internal val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    internal val store: TransactionStore = TransactionStore()

    /** Whether [start] has been called (and [stop] has not). */
    public val isStarted: Boolean get() = state.value.isStarted

    public val configuration: PantauHttpConfiguration get() = state.value.configuration

    /** All recorded transactions, newest first. */
    public val transactions: StateFlow<List<HttpTransaction>> get() = store.transactions

    /** Transactions recorded since the inspector was last shown. */
    public val unseenCount: StateFlow<Int> get() = store.unseenCount

    /** Set by the UI module; used by shake and notification taps. */
    public var presenter: InspectorPresenter? = null

    /** Recorder bound to the active configuration; engines fetch it per request. */
    internal val recorder: TransactionRecorder
        get() = recorderFlow.value

    private val recorderFlow = MutableStateFlow(makeRecorder(PantauHttpConfiguration()))

    private fun makeRecorder(configuration: PantauHttpConfiguration) = TransactionRecorder(
        store = store,
        configuration = configuration,
        onComplete = { tx ->
            NotificationService.transactionDidComplete(tx, configuration.notificationPolicy, store.unseenCount.value)
        },
    )

    /**
     * Starts capturing. Call early (Android `Application.onCreate`, iOS
     * `didFinishLaunching`) and before creating HTTP clients, ideally only in debug builds.
     */
    public fun start(configuration: PantauHttpConfiguration = PantauHttpConfiguration()) {
        state.update { State(configuration, isStarted = true) }
        store.maxTransactions = configuration.maxTransactions
        recorderFlow.value = makeRecorder(configuration)

        PlatformCapture.start(configuration)
        if (configuration.shakeEnabled) {
            ShakeDetector.install {
                if (isStarted && this.configuration.shakeEnabled) present()
            }
        } else {
            ShakeDetector.uninstall()
        }
        NotificationService.startIfNeeded(configuration.notificationPolicy)
        restartPusher(configuration)
    }

    /** Stops capturing. Recorded transactions are kept until [clearTransactions]. */
    public fun stop() {
        val wasStarted = state.value.isStarted
        state.update { State(it.configuration, isStarted = false) }
        if (wasStarted) PlatformCapture.stop()
        ShakeDetector.uninstall()
        restartPusher(null)
    }

    public fun clearTransactions() {
        store.clear()
    }

    /** Resets the unseen counter and removes the delivered activity notification. */
    public fun markAllSeen() {
        store.markAllSeen()
        NotificationService.clearDelivered()
    }

    public fun transaction(id: String): HttpTransaction? = store.get(id)

    public fun present() {
        runOnMainThread { presenter?.present() }
    }

    public fun dismiss() {
        runOnMainThread { presenter?.dismiss() }
    }

    /** True while a dashboard pusher is active (test hook). */
    internal val isPushingToDashboard: Boolean get() = pusher != null

    private fun restartPusher(configuration: PantauHttpConfiguration?) {
        val previous = pusher
        pusher = null
        previous?.stop()
        val url = configuration?.dashboardUrl ?: return
        val next = DashboardPusher(
            endpoint = url,
            device = currentDeviceIdentity(configuration.dashboardDeviceName),
            client = createInternalHttpClient(),
            scope = scope,
            lookup = store::get,
        )
        pusher = next
        next.start(store.upserts)
    }

    /** Swaps the pusher's HTTP engine; used by tests. */
    internal suspend fun <T> withPusherLock(block: suspend () -> T): T = pusherLock.withLock { block() }
}
