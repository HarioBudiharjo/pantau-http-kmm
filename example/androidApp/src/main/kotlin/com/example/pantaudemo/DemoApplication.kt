package com.example.pantaudemo

import android.app.Application
import com.pantauhttp.PantauHttp
import com.pantauhttp.PantauHttpConfiguration

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            PantauHttp.start(
                PantauHttpConfiguration(
                    dashboardUrl = BuildConfig.PANTAU_DASHBOARD_URL.ifBlank { null },
                    dashboardDeviceName = "Example app",
                ),
            )
        }
    }
}
