package com.pantauhttp.internal.remote

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.pantauhttp.internal.android.AndroidContextHolder
import java.util.UUID

@SuppressLint("HardwareIds")
internal actual fun currentDeviceIdentity(nameOverride: String?): DeviceIdentity {
    val context = AndroidContextHolder.applicationContextOrNull
    val name = nameOverride ?: deviceName(context) ?: Build.MODEL ?: "Android"
    val (appName, appVersion, packageName) = appInfo(context)
    return DeviceIdentity(
        id = stableId(context),
        name = name,
        model = Build.MODEL ?: "",
        systemName = "Android",
        systemVersion = Build.VERSION.RELEASE ?: "",
        appName = appName,
        appVersion = appVersion,
        bundleId = packageName,
        isSimulator = isEmulator(),
    )
}

@SuppressLint("HardwareIds")
private fun stableId(context: Context?): String {
    context ?: return processFallbackId
    val androidId = runCatching { Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) }.getOrNull()
    if (!androidId.isNullOrBlank() && androidId != "9774d56d682e549c") return androidId.lowercase()
    val prefs = context.getSharedPreferences("com.pantauhttp", Context.MODE_PRIVATE)
    prefs.getString("deviceId", null)?.let { return it }
    val generated = UUID.randomUUID().toString().lowercase()
    prefs.edit().putString("deviceId", generated).apply()
    return generated
}

private val processFallbackId: String by lazy { UUID.randomUUID().toString().lowercase() }

private fun deviceName(context: Context?): String? {
    context ?: return null
    return runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
        } else {
            null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }
}

private fun appInfo(context: Context?): Triple<String, String, String> {
    context ?: return Triple("App", "", "")
    val pm = context.packageManager
    val packageName = context.packageName
    val label = runCatching { pm.getApplicationLabel(context.applicationInfo).toString() }.getOrDefault("App")
    val version = runCatching {
        val info = pm.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
        val short = info.versionName
        if (short != null) "$short ($code)" else "($code)"
    }.getOrDefault("")
    return Triple(label, version, packageName)
}

private fun isEmulator(): Boolean {
    val fingerprint = Build.FINGERPRINT ?: ""
    val model = Build.MODEL ?: ""
    val hardware = Build.HARDWARE ?: ""
    val product = Build.PRODUCT ?: ""
    return fingerprint.startsWith("generic") || fingerprint.contains("emulator", ignoreCase = true) ||
        model.contains("Emulator") || model.contains("Android SDK built for") || model.startsWith("sdk_gphone") ||
        hardware.contains("goldfish") || hardware.contains("ranchu") ||
        product.contains("sdk") || product.contains("emulator")
}
