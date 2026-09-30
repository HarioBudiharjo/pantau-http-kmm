package com.pantauhttp.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import com.pantauhttp.InspectorPresenter

/** Shows the inspector in its own screen (Android Activity) or page sheet (iOS). */
internal expect object ComposeInspectorPresenter : InspectorPresenter {
    override fun present()
    override fun dismiss()
}

internal expect class ShareService {
    fun shareText(text: String, subject: String)
    fun shareFile(bytes: ByteArray, fileName: String, mimeType: String)
}

@Composable
internal expect fun rememberShareService(): ShareService

internal expect fun decodeImageBitmap(bytes: ByteArray): ImageBitmap?

/** System back navigation (Android); no-op where the platform has none. */
@Composable
internal expect fun InspectorBackHandler(enabled: Boolean, onBack: () -> Unit)
