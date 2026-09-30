package com.pantauhttp.internal.platform

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pantauhttp.PantauHttpConfiguration
import com.pantauhttp.android.PantauHttpAndroid
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import okhttp3.OkHttpClient
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

internal actual fun formatClockTime(epochMillis: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(epochMillis))

internal actual fun formatMediumDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM).format(Date(epochMillis))

internal actual fun logWarning(tag: String, message: String) {
    try {
        Log.w(tag, message)
    } catch (_: RuntimeException) {
        println("W/$tag: $message") // android.util.Log is not available in plain JVM unit tests
    }
}

/** Capture on Android is opt-in per OkHttp client via PantauHttpInterceptor; nothing global to register. */
internal actual object PlatformCapture {
    actual fun start(configuration: PantauHttpConfiguration) {}
    actual fun stop() {}
}

internal actual object AppState {
    actual val isForeground: Boolean get() = PantauHttpAndroid.isForeground
}

internal actual fun createInternalHttpClient(engine: HttpClientEngine?): HttpClient {
    val resolved = engine ?: OkHttp.create {
        preconfigured = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .build()
    }
    return HttpClient(resolved) {
        expectSuccess = false
        install(HttpTimeout) { requestTimeoutMillis = 5_000 }
    }
}

private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

internal actual fun runOnMainThread(block: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
}
