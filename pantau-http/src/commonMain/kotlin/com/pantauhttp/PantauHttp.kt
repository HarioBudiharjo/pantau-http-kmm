package com.pantauhttp

import com.pantauhttp.ui.platform.ComposeInspectorPresenter
import kotlinx.coroutines.flow.StateFlow

/**
 * An in-app HTTP(S) inspector for Kotlin Multiplatform — like Chucker, with a
 * shared Compose UI on Android and iOS.
 *
 * ```kotlin
 * PantauHttp.start()                                  // once, early, debug builds only
 * val client = HttpClient { install(PantauHttpPlugin) } // any Ktor client
 * PantauHttp.present()                                // or shake the device
 * ```
 */
public object PantauHttp {

    /** Whether [start] has been called (and [stop] has not). */
    public val isStarted: Boolean get() = PantauHttpCore.isStarted

    public val configuration: PantauHttpConfiguration get() = PantauHttpCore.configuration

    /** All recorded transactions, newest first. */
    public val transactions: StateFlow<List<HttpTransaction>> get() = PantauHttpCore.transactions

    /** Transactions recorded since the inspector was last shown. */
    public val unseenCount: StateFlow<Int> get() = PantauHttpCore.unseenCount

    /**
     * Starts capturing and installs shake detection and notifications per the
     * configuration. Call early (Android `Application.onCreate`, iOS
     * `didFinishLaunching`) and before creating HTTP clients.
     */
    public fun start(configuration: PantauHttpConfiguration = PantauHttpConfiguration()) {
        PantauHttpCore.presenter = ComposeInspectorPresenter
        PantauHttpCore.start(configuration)
    }

    /** Stops capturing. Recorded transactions are kept until [clearTransactions]. */
    public fun stop() {
        PantauHttpCore.stop()
    }

    /** Presents the inspector UI over the current screen. */
    public fun present() {
        PantauHttpCore.presenter = ComposeInspectorPresenter
        PantauHttpCore.present()
    }

    /** Dismisses the inspector UI if it is currently presented. */
    public fun dismiss() {
        PantauHttpCore.dismiss()
    }

    /** Removes all recorded transactions. */
    public fun clearTransactions() {
        PantauHttpCore.clearTransactions()
    }
}
