package com.pantauhttp.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import java.lang.ref.WeakReference

/** Hosts the inspector. Started by [com.pantauhttp.PantauHttp.present], shake, or the activity notification. */
public class PantauHttpActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        current = WeakReference(this)
        setContent { PantauHttpInspector(onClose = ::finish) }
    }

    override fun onDestroy() {
        if (current?.get() === this) current = null
        super.onDestroy()
    }

    internal companion object {
        @Volatile
        var current: WeakReference<PantauHttpActivity>? = null
    }
}
