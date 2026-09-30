package com.pantauhttp.internal.platform

import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSURLRequest
import platform.Foundation.allHTTPHeaderFields
import platform.Foundation.create
import platform.posix.memcpy

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    return ByteArray(size).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, length) }
    }
}

internal fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = size.toULong()) }
}

internal fun NSURLRequest.headerMap(): Map<String, String> =
    allHTTPHeaderFields?.entries?.associate { (key, value) -> key.toString() to value.toString() } ?: emptyMap()

internal fun NSHTTPURLResponse.headerMap(): Map<String, String> =
    allHeaderFields.entries.associate { (key, value) -> key.toString() to value.toString() }
