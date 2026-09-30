package com.pantauhttp.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pantauhttp.format.BodyFormatter
import com.pantauhttp.ui.platform.decodeImageBitmap

private const val INLINE_LINE_LIMIT = 2_000

@Composable
internal fun PayloadTab(headers: Map<String, String>, body: ByteArray?, contentType: String?, truncated: Boolean) {
    val headerLines = remember(headers) {
        if (headers.isEmpty()) listOf("(none)") else headers.entries.sortedBy { it.key }.map { "${it.key}: ${it.value}" }
    }
    val image = remember(body, contentType) {
        if (BodyFormatter.isImage(contentType) && body != null) decodeImageBitmap(body) else null
    }
    val bodyText = remember(body, contentType) { if (image == null) BodyFormatter.prettyPrinted(body, contentType) else "" }
    val bodyLines = remember(bodyText) { bodyText.lines() }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp)) {
        item { SectionLabel("HEADERS") }
        item { MonoBlock(headerLines.joinToString("\n")) }
        item { SectionLabel("BODY", topPadding = 20.dp) }
        when {
            image != null -> item {
                Image(
                    bitmap = image,
                    contentDescription = "Image body",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
                    contentScale = ContentScale.Fit,
                )
            }
            body == null || body.isEmpty() -> item { MonoBlock("(empty)") }
            bodyLines.size <= INLINE_LINE_LIMIT -> item { MonoBlock(bodyText) }
            else -> items(bodyLines.size) { index -> MonoLine(bodyLines[index]) }
        }
        if (truncated) {
            item {
                Text(
                    "(body truncated by size limit)",
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, topPadding: androidx.compose.ui.unit.Dp = 0.dp) {
    Text(
        text,
        modifier = Modifier.padding(top = topPadding, bottom = 6.dp),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MonoBlock(text: String) {
    SelectionContainer {
        Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MonoLine(text: String) {
    Text(text, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 17.sp, style = MaterialTheme.typography.bodySmall)
}
