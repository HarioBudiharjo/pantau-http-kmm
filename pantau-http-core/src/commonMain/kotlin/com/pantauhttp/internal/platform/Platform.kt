package com.pantauhttp.internal.platform

import com.pantauhttp.HttpTransaction
import com.pantauhttp.NotificationPolicy
import com.pantauhttp.PantauHttpConfiguration
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import kotlin.time.Clock

/** Epoch milliseconds now. */
internal fun epochMillis(): Long = Clock.System.now().toEpochMilliseconds()

/** Local wall-clock time "HH:mm:ss". */
internal expect fun formatClockTime(epochMillis: Long): String

/** Localized medium date+time for the overview screen. */
internal expect fun formatMediumDateTime(epochMillis: Long): String

internal expect fun logWarning(tag: String, message: String)

/** Registers/unregisters the platform-native capture engine (iOS URLProtocol; no-op on Android). */
internal expect object PlatformCapture {
    fun start(configuration: PantauHttpConfiguration)
    fun stop()
}

internal expect object ShakeDetector {
    /** Idempotent. [onShake] is invoked on the main thread. */
    fun install(onShake: () -> Unit)
    fun uninstall()
}

internal expect object NotificationService {
    fun startIfNeeded(policy: NotificationPolicy)
    fun transactionDidComplete(transaction: HttpTransaction, policy: NotificationPolicy, unseenCount: Int)
    fun clearDelivered()
}

internal expect object AppState {
    val isForeground: Boolean
}

/**
 * A client for the library's own traffic (dashboard pusher). Must never be
 * observed by any capture engine.
 */
internal expect fun createInternalHttpClient(engine: HttpClientEngine? = null): HttpClient

/** Runs [block] on the platform main thread (immediately if already there). */
internal expect fun runOnMainThread(block: () -> Unit)
