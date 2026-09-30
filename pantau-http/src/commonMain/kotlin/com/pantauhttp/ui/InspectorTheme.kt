package com.pantauhttp.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.pantauhttp.HttpTransaction
import com.pantauhttp.TransactionState

@Composable
internal fun PantauHttpTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}

internal object InspectorColors {
    val gray = Color(0xFF8E8E93)
    val green = Color(0xFF34A853)
    val blue = Color(0xFF1A73E8)
    val orange = Color(0xFFF29900)
    val red = Color(0xFFD93025)
    val purple = Color(0xFF8E44AD)

    fun status(transaction: HttpTransaction): Color = when (transaction.state) {
        TransactionState.InProgress -> gray
        TransactionState.Failed -> red
        TransactionState.Completed -> when (transaction.statusCode ?: 0) {
            in 200..299 -> green
            in 300..399 -> blue
            in 400..499 -> orange
            in 500..599 -> red
            else -> gray
        }
    }

    fun method(method: String): Color = when (method.uppercase()) {
        "GET" -> blue
        "POST" -> green
        "PUT", "PATCH" -> orange
        "DELETE" -> red
        "HEAD", "OPTIONS" -> purple
        else -> gray
    }
}
