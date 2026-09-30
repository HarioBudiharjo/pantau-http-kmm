package com.pantauhttp.internal.platform

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import com.pantauhttp.internal.android.AndroidContextHolder
import kotlin.math.sqrt

/** Accelerometer-based shake detection (g-force spike twice within 500 ms). */
internal actual object ShakeDetector {

    private const val THRESHOLD_G = 2.7f
    private const val WINDOW_MS = 500L
    private const val COOLDOWN_MS = 1_000L

    private var listener: SensorEventListener? = null

    actual fun install(onShake: () -> Unit) {
        if (listener != null) return
        val context = AndroidContextHolder.applicationContextOrNull ?: return
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return
        val sensor = manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return

        val newListener = object : SensorEventListener {
            private var firstSpikeAt = 0L
            private var lastShakeAt = 0L

            override fun onSensorChanged(event: SensorEvent) {
                val gX = event.values[0] / SensorManager.GRAVITY_EARTH
                val gY = event.values[1] / SensorManager.GRAVITY_EARTH
                val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
                val force = sqrt(gX * gX + gY * gY + gZ * gZ)
                if (force < THRESHOLD_G) return
                val now = SystemClock.elapsedRealtime()
                if (now - lastShakeAt < COOLDOWN_MS) return
                if (now - firstSpikeAt > WINDOW_MS) {
                    firstSpikeAt = now
                    return
                }
                firstSpikeAt = 0L
                lastShakeAt = now
                if (AppState.isForeground) runOnMainThread(onShake)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        listener = newListener
        manager.registerListener(newListener, sensor, SensorManager.SENSOR_DELAY_GAME)
    }

    actual fun uninstall() {
        val current = listener ?: return
        listener = null
        val context = AndroidContextHolder.applicationContextOrNull ?: return
        (context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager)?.unregisterListener(current)
    }
}
