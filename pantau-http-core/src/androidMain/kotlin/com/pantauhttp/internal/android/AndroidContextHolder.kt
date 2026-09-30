package com.pantauhttp.internal.android

import android.content.Context

internal object AndroidContextHolder {
    @Volatile
    private var context: Context? = null

    fun set(applicationContext: Context) {
        context = applicationContext
    }

    val applicationContextOrNull: Context? get() = context

    fun require(): Context = context ?: error(
        "PantauHTTP has no Android Context. Keep PantauHttpInitProvider in the manifest " +
            "or call PantauHttpAndroid.init(context) before PantauHttp.start().",
    )
}
