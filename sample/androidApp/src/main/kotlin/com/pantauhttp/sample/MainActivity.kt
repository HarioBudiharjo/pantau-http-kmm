package com.pantauhttp.sample

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class MainActivity : ComponentActivity() {

    /** Ktor client: the plugin records everything this client does. */
    private val ktor = HttpClient(OkHttp) { install(PantauHttpPlugin) }

    /** Plain OkHttp (Retrofit/Coil-style): the interceptor records it. */
    private val okHttp = OkHttpClient.Builder().addInterceptor(PantauHttpInterceptor()).build()

    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        val autofire = intent.getBooleanExtra("autofire", false)

        setContent {
            MaterialTheme {
                val scope = rememberCoroutineScope()
                val log = remember { mutableStateListOf("Shake the device to open the inspector.") }
                fun run(label: String, block: suspend () -> String) {
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { block() } }
                        log.add(0, "$label → ${result.getOrElse { it.message ?: it::class.simpleName ?: "error" }}")
                    }
                }
                val actions = listOf<Pair<String, () -> Unit>>(
                    "GET JSON (Ktor)" to { run("GET /users") { ktor.get("https://jsonplaceholder.typicode.com/users").status.toString() } },
                    "POST JSON with Bearer (Ktor)" to {
                        run("POST /posts") {
                            ktor.post("https://jsonplaceholder.typicode.com/posts") {
                                header("Authorization", "Bearer super-secret-token")
                                contentType(ContentType.Application.Json)
                                setBody("""{"title":"pantau","body":"hello","userId":1}""")
                            }.status.toString()
                        }
                    },
                    "404 (Ktor)" to { run("GET /nope") { ktor.get("https://jsonplaceholder.typicode.com/nope/404").status.toString() } },
                    "Redirect chain (Ktor)" to { run("GET /redirect/2") { ktor.get("https://httpbin.org/redirect/2").status.toString() } },
                    "Download image (Ktor)" to { run("GET /image/png") { "${ktor.get("https://httpbin.org/image/png").bodyAsBytes().size} bytes" } },
                    "GET via OkHttp interceptor" to {
                        run("OkHttp GET /todos/1") {
                            okHttp.newCall(Request.Builder().url("https://jsonplaceholder.typicode.com/todos/1").build()).execute().use { it.code.toString() }
                        }
                    },
                    "Failing host (OkHttp)" to {
                        run("OkHttp GET invalid host") {
                            okHttp.newCall(Request.Builder().url("https://this-host-does-not-exist.invalid/api").build()).execute().use { it.code.toString() }
                        }
                    },
                )
                LaunchedEffect(autofire) { if (autofire) actions.forEach { it.second() } }

                Scaffold(topBar = { TopAppBar(title = { Text("PantauHTTP Sample") }) }) { padding ->
                    Column(
                        modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        actions.forEach { (label, action) ->
                            OutlinedButton(onClick = action, modifier = Modifier.fillMaxWidth()) { Text(label) }
                        }
                        Button(onClick = { PantauHttp.present() }, modifier = Modifier.fillMaxWidth()) { Text("Open Inspector") }
                        LogView(log)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogView(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall) }
    }
}
