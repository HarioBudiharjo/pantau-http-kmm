package com.pantauhttp.internal.platform

import com.pantauhttp.PantauHttpConfiguration
import com.pantauhttp.internal.capture.PantauUrlProtocol
import com.pantauhttp.internal.capture.installProtocolGate
import com.pantauhttp.shim.PantauShakeInstall
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import platform.Foundation.NSDate
import platform.Foundation.NSDateFormatter
import platform.Foundation.NSDateFormatterMediumStyle
import platform.Foundation.NSLog
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSThread
import platform.Foundation.NSURLProtocol
import platform.Foundation.dateWithTimeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIApplicationState
import platform.UIKit.UIApplicationWillResignActiveNotification
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.concurrent.Volatile

private val clockFormatter: NSDateFormatter by lazy { NSDateFormatter().apply { dateFormat = "HH:mm:ss" } }
private val mediumFormatter: NSDateFormatter by lazy {
    NSDateFormatter().apply { dateStyle = NSDateFormatterMediumStyle; timeStyle = NSDateFormatterMediumStyle }
}

internal actual fun formatClockTime(epochMillis: Long): String =
    clockFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

internal actual fun formatMediumDateTime(epochMillis: Long): String =
    mediumFormatter.stringFromDate(NSDate.dateWithTimeIntervalSince1970(epochMillis / 1000.0))

internal actual fun logWarning(tag: String, message: String) {
    // No varargs: Kotlin strings do not bridge through NSLog's variadic %@ arguments.
    NSLog("[$tag] $message".replace("%", "%%"))
}

/** Registers the URLProtocol engine for URLSession.shared and default-configuration sessions. */
internal actual object PlatformCapture {
    private var registered = false

    actual fun start(configuration: PantauHttpConfiguration) {
        installProtocolGate()
        if (!registered) {
            registered = NSURLProtocol.registerClass(PantauUrlProtocol)
        }
        AppState.startObserving()
    }

    actual fun stop() {
        if (registered) {
            NSURLProtocol.unregisterClass(PantauUrlProtocol)
            registered = false
        }
    }
}

internal actual object ShakeDetector {
    @Volatile
    private var handler: (() -> Unit)? = null

    actual fun install(onShake: () -> Unit) {
        handler = onShake
        PantauShakeInstall { handler?.let(::runOnMainThread) }
    }

    actual fun uninstall() {
        handler = null
    }
}

internal actual object AppState {
    @Volatile
    private var foreground = true
    private var observing = false

    actual val isForeground: Boolean get() = foreground

    fun startObserving() {
        runOnMainThread {
            if (observing) return@runOnMainThread
            observing = true
            foreground = UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive
            val center = NSNotificationCenter.defaultCenter
            center.addObserverForName(UIApplicationDidBecomeActiveNotification, null, NSOperationQueue.mainQueue) { foreground = true }
            center.addObserverForName(UIApplicationWillResignActiveNotification, null, NSOperationQueue.mainQueue) { foreground = false }
        }
    }
}

internal actual fun createInternalHttpClient(engine: HttpClientEngine?): HttpClient {
    val resolved = engine ?: Darwin.create {
        configureSession {
            // The pusher's own traffic must never be captured: no custom protocols.
            setProtocolClasses(null)
            setTimeoutIntervalForRequest(5.0)
            setWaitsForConnectivity(false)
            setHTTPMaximumConnectionsPerHost(2)
        }
    }
    return HttpClient(resolved) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 5_000 }
    }
}

internal actual fun runOnMainThread(block: () -> Unit) {
    if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
}
