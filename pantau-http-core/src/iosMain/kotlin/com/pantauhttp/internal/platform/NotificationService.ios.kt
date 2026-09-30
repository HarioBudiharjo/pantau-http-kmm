package com.pantauhttp.internal.platform

import com.pantauhttp.HttpTransaction
import com.pantauhttp.NotificationPolicy
import com.pantauhttp.PantauHttpCore
import platform.Foundation.NSLock
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotification
import platform.UserNotifications.UNNotificationPresentationOptionAlert
import platform.UserNotifications.UNNotificationPresentationOptionBanner
import platform.UserNotifications.UNNotificationPresentationOptions
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationResponse
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNUserNotificationCenterDelegateProtocol
import platform.darwin.NSObject

/** Posts a single coalesced "HTTP activity" local notification and opens the inspector on tap. */
internal actual object NotificationService {

    private const val IDENTIFIER = "pantauhttp.activity"

    private val lock = NSLock()
    private var didRequestAuthorization = false
    private var authorizationGranted = false
    private var installedInternalDelegate = false

    private val delegate = object : NSObject(), UNUserNotificationCenterDelegateProtocol {
        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            didReceiveNotificationResponse: UNNotificationResponse,
            withCompletionHandler: () -> Unit,
        ) {
            handle(didReceiveNotificationResponse)
            withCompletionHandler()
        }

        override fun userNotificationCenter(
            center: UNUserNotificationCenter,
            willPresentNotification: UNNotification,
            withCompletionHandler: (UNNotificationPresentationOptions) -> Unit,
        ) {
            val ours = willPresentNotification.request.identifier == IDENTIFIER
            if (ours && PantauHttpCore.configuration.notificationPolicy == NotificationPolicy.Always) {
                withCompletionHandler(UNNotificationPresentationOptionBanner or UNNotificationPresentationOptionAlert)
            } else {
                withCompletionHandler(0u)
            }
        }
    }

    actual fun startIfNeeded(policy: NotificationPolicy) {
        if (policy == NotificationPolicy.Never) return
        runOnMainThread {
            val center = UNUserNotificationCenter.currentNotificationCenter()
            // Only claim the delegate when the host app has none; otherwise the
            // host forwards taps via PantauHttpIos.handleNotification.
            if (center.delegate == null) {
                center.delegate = delegate
                installedInternalDelegate = true
            }
        }
    }

    actual fun transactionDidComplete(transaction: HttpTransaction, policy: NotificationPolicy, unseenCount: Int) {
        if (policy == NotificationPolicy.Never) return
        runOnMainThread {
            if (policy == NotificationPolicy.WhenBackgrounded && AppState.isForeground) return@runOnMainThread
            requestAuthorizationIfNeeded { granted ->
                if (granted) post(transaction, unseenCount)
            }
        }
    }

    actual fun clearDelivered() {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeDeliveredNotificationsWithIdentifiers(listOf(IDENTIFIER))
        center.removePendingNotificationRequestsWithIdentifiers(listOf(IDENTIFIER))
    }

    /** Returns true when the response belonged to PantauHTTP. */
    fun handle(response: UNNotificationResponse): Boolean {
        if (response.notification.request.identifier != IDENTIFIER) return false
        PantauHttpCore.present()
        return true
    }

    private fun requestAuthorizationIfNeeded(completion: (Boolean) -> Unit) {
        lock.lock()
        if (didRequestAuthorization) {
            val granted = authorizationGranted
            lock.unlock()
            completion(granted)
            return
        }
        didRequestAuthorization = true
        lock.unlock()
        UNUserNotificationCenter.currentNotificationCenter().requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionBadge,
        ) { granted, _ ->
            lock.lock()
            authorizationGranted = granted
            lock.unlock()
            completion(granted)
        }
    }

    private fun post(transaction: HttpTransaction, unseenCount: Int) {
        val plural = if (unseenCount == 1) "" else "s"
        val content = UNMutableNotificationContent().apply {
            setTitle("Pantau HTTP")
            setBody("$unseenCount transaction$plural recorded — ${transaction.method} ${transaction.path} (${transaction.statusText})")
        }
        val center = UNUserNotificationCenter.currentNotificationCenter()
        center.removeDeliveredNotificationsWithIdentifiers(listOf(IDENTIFIER))
        val request = UNNotificationRequest.requestWithIdentifier(IDENTIFIER, content, trigger = null)
        center.addNotificationRequest(request, withCompletionHandler = null)
    }
}
