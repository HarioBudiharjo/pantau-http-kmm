package com.pantauhttp.android

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import com.pantauhttp.internal.android.AndroidContextHolder
import java.util.concurrent.atomic.AtomicInteger

/**
 * Android-specific entry points. Initialisation normally happens automatically
 * through [PantauHttpInitProvider]; call [init] yourself only if you removed the
 * provider from the manifest or run in an isolated process.
 */
public object PantauHttpAndroid {

    private val startedActivities = AtomicInteger(0)
    private var callbacksRegistered = false

    public fun init(context: Context) {
        val app = context.applicationContext
        AndroidContextHolder.set(app)
        if (app is Application && !callbacksRegistered) {
            callbacksRegistered = true
            app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
                override fun onActivityStarted(activity: Activity) { startedActivities.incrementAndGet() }
                override fun onActivityStopped(activity: Activity) { startedActivities.decrementAndGet() }
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
                override fun onActivityResumed(activity: Activity) {}
                override fun onActivityPaused(activity: Activity) {}
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
                override fun onActivityDestroyed(activity: Activity) {}
            })
        }
    }

    internal val isForeground: Boolean get() = startedActivities.get() > 0
}
