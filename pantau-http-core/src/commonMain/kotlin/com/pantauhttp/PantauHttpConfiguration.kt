package com.pantauhttp

/** When to post the coalesced "HTTP activity" local notification. */
public enum class NotificationPolicy {
    /** Never post local notifications. */
    Never,
    /** Post a notification only while the app is in the background (default). */
    WhenBackgrounded,
    /** Post a notification for every completed transaction. */
    Always,
}

/** Options controlling what PantauHTTP captures and how it behaves. */
public data class PantauHttpConfiguration(
    /** Maximum number of transactions kept in memory. Oldest are evicted first. */
    val maxTransactions: Int = 200,
    /**
     * Maximum number of bytes stored per request/response body. The app always
     * receives the full body; only the inspector's copy is capped.
     */
    val bodySizeLimit: Int = 1_048_576,
    /** Present the inspector when the device is shaken. */
    val shakeEnabled: Boolean = true,
    val notificationPolicy: NotificationPolicy = NotificationPolicy.WhenBackgrounded,
    /**
     * Header names (case-insensitive) whose values are replaced with "••••••••"
     * before being stored. The original value is never retained.
     */
    val redactedHeaders: Set<String> = setOf("Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization"),
    /** Hosts to skip entirely (exact, case-insensitive match). Empty captures all hosts. */
    val ignoredHosts: Set<String> = emptySet(),
    /**
     * Base URL of a running PantauHTTP dashboard server (e.g. `http://192.168.1.20:9435`).
     * When set, every captured transaction is also pushed there over the local network.
     * Headers are redacted and bodies capped before anything leaves the device.
     */
    val dashboardUrl: String? = null,
    /** Name shown for this device in the dashboard's device list. */
    val dashboardDeviceName: String? = null,
)

internal const val REDACTED_VALUE: String = "••••••••"

internal fun PantauHttpConfiguration.shouldCapture(host: String?): Boolean {
    val lowered = host?.lowercase() ?: return true
    return ignoredHosts.none { it.lowercase() == lowered }
}

internal fun PantauHttpConfiguration.redact(headers: Map<String, String>): Map<String, String> {
    if (redactedHeaders.isEmpty()) return headers
    val lowered = redactedHeaders.mapTo(HashSet()) { it.lowercase() }
    return headers.mapValues { (key, value) -> if (key.lowercase() in lowered) REDACTED_VALUE else value }
}
