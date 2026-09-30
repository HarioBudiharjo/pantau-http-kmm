package com.pantauhttp.internal.platform

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.pantauhttp.HttpTransaction
import com.pantauhttp.NotificationPolicy
import com.pantauhttp.internal.android.AndroidContextHolder

/** Posts a single coalesced "HTTP activity" notification (Chucker-style). */
internal actual object NotificationService {

    private const val CHANNEL_ID = "pantauhttp.activity"
    private const val NOTIFICATION_ID = 0x9435

    /** Intent action handled by the UI module's inspector activity. */
    internal const val OPEN_INSPECTOR_ACTION = "com.pantauhttp.OPEN_INSPECTOR"

    actual fun startIfNeeded(policy: NotificationPolicy) {
        if (policy == NotificationPolicy.Never) return
        val context = AndroidContextHolder.applicationContextOrNull ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "Pantau HTTP", NotificationManager.IMPORTANCE_DEFAULT).apply {
                        description = "Recent HTTP activity captured by PantauHTTP"
                        setShowBadge(false)
                    },
                )
            }
        }
    }

    actual fun transactionDidComplete(transaction: HttpTransaction, policy: NotificationPolicy, unseenCount: Int) {
        if (policy == NotificationPolicy.Never) return
        if (policy == NotificationPolicy.WhenBackgrounded && AppState.isForeground) return
        val context = AndroidContextHolder.applicationContextOrNull ?: return
        val compat = NotificationManagerCompat.from(context)
        if (!compat.areNotificationsEnabled()) return
        startIfNeeded(policy)

        val openIntent = Intent(OPEN_INSPECTOR_ACTION).setPackage(context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val hasTarget = context.packageManager.resolveActivity(openIntent, 0) != null
        val pendingIntent = if (hasTarget) {
            PendingIntent.getActivity(context, 0, openIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        } else {
            null
        }

        val plural = if (unseenCount == 1) "" else "s"
        val body = "$unseenCount transaction$plural recorded — ${transaction.method} ${transaction.path} (${transaction.statusText})"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Pantau HTTP")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            compat.notify(NOTIFICATION_ID, notification)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS not granted by the host app on API 33+.
        }
    }

    actual fun clearDelivered() {
        val context = AndroidContextHolder.applicationContextOrNull ?: return
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
