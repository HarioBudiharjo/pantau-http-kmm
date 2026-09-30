package com.pantauhttp

import com.pantauhttp.internal.capture.SessionInjector
import com.pantauhttp.internal.platform.NotificationService
import platform.Foundation.NSURLSessionConfiguration
import platform.UserNotifications.UNNotificationResponse

/** iOS-only entry points, mirroring the original Swift `PantauHTTP` API. */
public object PantauHttpIos {

    /**
     * Injects the capture engine into a custom `URLSessionConfiguration`. Call
     * BEFORE creating the `URLSession` (or Alamofire `Session`).
     */
    public fun enable(configuration: NSURLSessionConfiguration) {
        SessionInjector.enable(configuration)
    }

    /**
     * Call from your `UNUserNotificationCenterDelegate` if your app owns the
     * notification-center delegate. Returns true when the response belonged to
     * PantauHTTP and was handled (the inspector was presented).
     */
    public fun handleNotification(response: UNNotificationResponse): Boolean =
        NotificationService.handle(response)
}

internal fun PantauHttpCore.installGateForTests() {
    com.pantauhttp.internal.capture.installProtocolGate()
}
