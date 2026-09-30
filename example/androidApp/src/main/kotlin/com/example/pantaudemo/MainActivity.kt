package com.example.pantaudemo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pantauhttp.PantauHttp
import com.pantauhttp.android.PantauHttpInterceptor
import com.pantauhttp.ktor.PantauHttpPlugin
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    private val ktor = HttpClient(OkHttp) { install(PantauHttpPlugin) }
    private val okHttp = OkHttpClient.Builder().addInterceptor(PantauHttpInterceptor()).build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autofire = intent.getBooleanExtra("autofire", false)
        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                val log = remember { mutableStateListOf("PantauHTTP ${if (PantauHttp.isStarted) "is running" else "is NOT running"}") }
                fun run(label: String, block: suspend () -> String) {
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { block() } }
                        log.add(0, "$label → ${result.getOrElse { it.message ?: "error" }}")
                    }
                }
                val actions = listOf<Pair<String, () -> Unit>>(
                    "GET (Ktor)" to { run("GET /users/1") { ktor.get("https://jsonplaceholder.typicode.com/users/1").status.toString() } },
                    "POST with Bearer (Ktor)" to {
                        run("POST /posts") {
                            ktor.post("https://jsonplaceholder.typicode.com/posts") {
                                header("Authorization", "Bearer secret-token")
                                contentType(ContentType.Application.Json)
                                setBody("""{"title":"demo"}""")
                            }.status.toString()
                        }
                    },
                    "GET (OkHttp interceptor)" to {
                        run("OkHttp GET /todos/2") {
                            okHttp.newCall(Request.Builder().url("https://jsonplaceholder.typicode.com/todos/2").build()).execute().use { it.code.toString() }
                        }
                    },
                    "Unread body (OkHttp)" to {
                        run("OkHttp GET /todos/3 (body not read)") {
                            okHttp.newCall(Request.Builder().url("https://jsonplaceholder.typicode.com/todos/3").build()).execute().code.toString()
                        }
                    },
                )
                LaunchedEffect(autofire) { if (autofire) actions.forEach { it.second() } }
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("PantauHTTP example (SDK from GitHub)", style = MaterialTheme.typography.titleLarge)
                    actions.forEach { (label, action) -> OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) } }
                    Button(onClick = { PantauHttp.present() }, modifier = Modifier.fillMaxWidth()) { Text("Open Inspector") }
                    log.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
