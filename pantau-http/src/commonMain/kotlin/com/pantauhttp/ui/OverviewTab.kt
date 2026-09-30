package com.pantauhttp.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.pantauhttp.HttpTransaction
import com.pantauhttp.format.ByteCount

@Composable
internal fun OverviewTab(transaction: HttpTransaction) {
    val rows = buildList {
        add("URL" to transaction.url)
        add("Method" to transaction.method)
        add("Status" to transaction.statusText)
        add("State" to transaction.state.wireName)
        add("Secure" to if (transaction.isSecure) "Yes (HTTPS)" else "No")
        add("Requested at" to transaction.requestDateText)
        transaction.responseDateText?.let { add("Responded at" to it) }
        if (transaction.durationText.isNotEmpty()) add("Duration" to transaction.durationText)
        add("Request size" to ByteCount.binary(transaction.requestBodySize) + if (transaction.isRequestBodyTruncated) " (stored copy truncated)" else "")
        add("Response size" to transaction.sizeText + if (transaction.isResponseBodyTruncated) " (stored copy truncated)" else "")
        transaction.errorDescription?.let { add("Error" to it) }
        transaction.redirects.forEach { add("Redirect (${it.statusCode})" to "${it.fromUrl}\n→ ${it.toUrl}") }
    }
    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    rows.forEachIndexed { index, (label, value) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            SelectionContainer {
                                Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = if (label == "URL") FontFamily.Monospace else FontFamily.Default)
                            }
                        }
                        if (index < rows.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}
