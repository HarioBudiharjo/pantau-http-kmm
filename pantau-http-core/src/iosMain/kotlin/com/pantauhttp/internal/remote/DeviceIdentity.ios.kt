package com.pantauhttp.internal.remote

import com.pantauhttp.shim.PantauIsSimulator
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSBundle
import platform.Foundation.NSProcessInfo
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDefaults
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

private const val PERSISTED_ID_KEY = "com.pantauhttp.deviceId"

internal actual fun currentDeviceIdentity(nameOverride: String?): DeviceIdentity {
    val device = UIDevice.currentDevice
    val info = NSBundle.mainBundle.infoDictionary ?: emptyMap<Any?, Any?>()
    val shortVersion = info["CFBundleShortVersionString"] as? String
    val build = info["CFBundleVersion"] as? String
    val appVersion = when {
        shortVersion != null && build != null -> "$shortVersion ($build)"
        shortVersion != null -> shortVersion
        build != null -> "($build)"
        else -> ""
    }
    return DeviceIdentity(
        id = stableId(),
        name = nameOverride ?: device.name,
        model = hardwareModel(device),
        systemName = device.systemName,
        systemVersion = device.systemVersion,
        appName = (info["CFBundleDisplayName"] as? String) ?: (info["CFBundleName"] as? String) ?: "App",
        appVersion = appVersion,
        bundleId = NSBundle.mainBundle.bundleIdentifier ?: "",
        isSimulator = isSimulator(),
    )
}

internal fun isSimulator(): Boolean = PantauIsSimulator()

private fun stableId(): String {
    UIDevice.currentDevice.identifierForVendor?.UUIDString?.let { return it.lowercase() }
    val defaults = NSUserDefaults.standardUserDefaults
    defaults.stringForKey(PERSISTED_ID_KEY)?.let { return it }
    val generated = NSUUID().UUIDString.lowercase()
    defaults.setObject(generated, PERSISTED_ID_KEY)
    return generated
}

private fun hardwareModel(device: UIDevice): String {
    (NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String)?.let { return it }
    val machine = memScoped {
        val info = alloc<utsname>()
        uname(info.ptr)
        info.machine.toKString()
    }
    return machine.ifEmpty { device.model }
}
