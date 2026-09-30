package com.pantauhttp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pantauhttp.PantauHttpCore
import com.pantauhttp.export.PantauHttpExports
import com.pantauhttp.ui.platform.rememberShareService
import kotlinx.coroutines.flow.map

private val tabs = listOf("Overview", "Request", "Response")

@Composable
internal fun TransactionDetailScreen(id: String, onBack: () -> Unit) {
    val flow = remember(id) { PantauHttpCore.transactions.map { list -> list.firstOrNull { it.id == id } } }
    val transaction by flow.collectAsState(initial = PantauHttpCore.transaction(id))
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var shareMenu by remember { mutableStateOf(false) }
    val share = rememberShareService()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(transaction?.path ?: "Transaction", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    val tx = transaction
                    if (tx != null) {
                        IconButton(onClick = { shareMenu = true }) { Icon(Icons.Default.Share, contentDescription = "Share") }
                        DropdownMenu(expanded = shareMenu, onDismissRequest = { shareMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Share as cURL") },
                                onClick = { shareMenu = false; share.shareText(PantauHttpExports.curl(tx), "cURL — ${tx.method} ${tx.path}") },
                            )
                            DropdownMenuItem(
                                text = { Text("Share as plain text") },
                                onClick = { shareMenu = false; share.shareText(PantauHttpExports.text(tx), "${tx.method} ${tx.path}") },
                            )
                            DropdownMenuItem(
                                text = { Text("Share as HAR file") },
                                onClick = {
                                    shareMenu = false
                                    share.shareFile(PantauHttpExports.har(tx).encodeToByteArray(), "transaction-${tx.id}.har", "application/json")
                                },
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            TabRow(selectedTabIndex = tab) {
                tabs.forEachIndexed { index, title ->
                    Tab(selected = tab == index, onClick = { tab = index }, text = { Text(title) })
                }
            }
            val tx = transaction
            if (tx == null) {
                Text("This transaction is no longer available.", modifier = Modifier.padding(16.dp))
            } else {
                when (tab) {
                    0 -> OverviewTab(tx)
                    1 -> PayloadTab(
                        headers = tx.requestHeaders,
                        body = tx.requestBody,
                        contentType = tx.requestContentType,
                        truncated = tx.isRequestBodyTruncated,
                    )
                    else -> PayloadTab(
                        headers = tx.responseHeaders,
                        body = tx.responseBody,
                        contentType = tx.responseContentType,
                        truncated = tx.isResponseBodyTruncated,
                    )
                }
            }
        }
    }
}
