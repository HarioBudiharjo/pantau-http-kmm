package com.pantauhttp.sample

import android.app.Application
import com.pantauhttp.PantauHttp
import com.pantauhttp.PantauHttpConfiguration

class SampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            // No Context needed: the library's init provider captured it already.
            PantauHttp.start(
                PantauHttpConfiguration(
                    dashboardUrl = BuildConfig.PANTAU_DASHBOARD_URL.ifBlank { null },
                    dashboardDeviceName = "Android sample",
                ),
            )
        }
    }
}
