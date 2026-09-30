package com.pantauhttp.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pantauhttp.ui.platform.InspectorBackHandler
import androidx.compose.foundation.layout.fillMaxSize

/**
 * The complete inspector UI (list + detail), embeddable anywhere in your own
 * Compose hierarchy. [PantauHttp.present] shows this in its own screen/sheet.
 */
@Composable
public fun PantauHttpInspector(onClose: () -> Unit) {
    PantauHttpTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
            InspectorBackHandler(enabled = selectedId != null) { selectedId = null }
            AnimatedContent(
                targetState = selectedId,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it / 4 } + fadeIn()) togetherWith fadeOut()
                    } else {
                        fadeIn() togetherWith (slideOutHorizontally { it / 4 } + fadeOut())
                    }
                },
                label = "inspector",
            ) { id ->
                if (id == null) {
                    TransactionListScreen(onSelect = { selectedId = it }, onClose = onClose)
                } else {
                    TransactionDetailScreen(id = id, onBack = { selectedId = null })
                }
            }
        }
    }
}
