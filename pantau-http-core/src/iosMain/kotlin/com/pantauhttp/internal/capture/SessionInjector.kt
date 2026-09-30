package com.pantauhttp.internal.capture

import kotlinx.cinterop.ObjCClass
import platform.Foundation.NSStringFromClass
import platform.Foundation.NSURLSessionConfiguration

internal object SessionInjector {

    private val protocolName: String get() = NSStringFromClass(PantauUrlProtocol)

    /** Inserts the capture protocol at index 0 of `protocolClasses`; no-op if present. */
    fun enable(configuration: NSURLSessionConfiguration) {
        val current = configuration.protocolClasses.orEmpty()
        if (isEnabled(configuration)) return
        configuration.setProtocolClasses(listOf<Any?>(PantauUrlProtocol) + current)
    }

    fun isEnabled(configuration: NSURLSessionConfiguration): Boolean =
        configuration.protocolClasses.orEmpty().any { entry ->
            entry === PantauUrlProtocol || (entry as? ObjCClass)?.let { NSStringFromClass(it) == protocolName } == true
        }
}
