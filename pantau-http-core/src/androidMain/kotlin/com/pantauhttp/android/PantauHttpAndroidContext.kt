package com.pantauhttp.android

import android.content.Context
import com.pantauhttp.internal.android.AndroidContextHolder

/** Read access to the application Context captured at init, for companion modules. */
public object PantauHttpAndroidContext {
    public fun applicationContext(): Context? = AndroidContextHolder.applicationContextOrNull
}
