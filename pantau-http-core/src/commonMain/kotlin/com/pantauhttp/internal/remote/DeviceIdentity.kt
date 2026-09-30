package com.pantauhttp.internal.remote

import kotlinx.serialization.Serializable

/**
 * Identifies the device/app pushing to the dashboard so the server can group
 * transactions per device. Field names are part of the dashboard wire contract.
 */
@Serializable
internal data class DeviceIdentity(
    val id: String,
    val name: String,
    val model: String,
    val systemName: String,
    val systemVersion: String,
    val appName: String,
    val appVersion: String,
    val bundleId: String,
    val isSimulator: Boolean,
)

internal expect fun currentDeviceIdentity(nameOverride: String?): DeviceIdentity
