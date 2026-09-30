package com.pantauhttp.ui.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.interop.LocalUIViewController
import androidx.compose.ui.window.ComposeUIViewController
import com.pantauhttp.InspectorPresenter
import com.pantauhttp.ui.PantauHttpInspector
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Image
import platform.Foundation.NSData
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSThread
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.writeToFile
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIModalPresentationPageSheet
import platform.UIKit.UINavigationController
import platform.UIKit.UISceneActivationStateForegroundActive
import platform.UIKit.UISceneActivationStateForegroundInactive
import platform.UIKit.UITabBarController
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UIKit.popoverPresentationController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.native.ref.WeakReference

internal actual object ComposeInspectorPresenter : InspectorPresenter {

    private var presented: WeakReference<UIViewController>? = null

    actual override fun present() {
        onMain {
            val existing = presented?.get()
            if (existing != null && existing.presentingViewController != null) return@onMain
            val top = topViewController() ?: return@onMain
            // A debug tool must not crash the host app over a missing plist key; the README still recommends
            // CADisableMinimumFrameDurationOnPhone=true for 120 Hz rendering.
            val controller = ComposeUIViewController(configure = { enforceStrictPlistSanityCheck = false }) {
                PantauHttpInspector(onClose = ::dismiss)
            }
            controller.modalPresentationStyle = UIModalPresentationPageSheet
            presented = WeakReference(controller)
            top.presentViewController(controller, animated = true, completion = null)
        }
    }

    actual override fun dismiss() {
        onMain {
            val controller = presented?.get()
            presented = null
            if (controller != null && controller.presentingViewController != null) {
                controller.dismissViewControllerAnimated(true, completion = null)
            }
        }
    }

    private fun onMain(block: () -> Unit) {
        if (NSThread.isMainThread) block() else dispatch_async(dispatch_get_main_queue()) { block() }
    }

    /** Top-most view controller of the most active foreground window. */
    private fun topViewController(): UIViewController? {
        val scenes = UIApplication.sharedApplication.connectedScenes.mapNotNull { it as? UIWindowScene }
        val ranked = scenes.sortedBy { scene ->
            when (scene.activationState) {
                UISceneActivationStateForegroundActive -> 0
                UISceneActivationStateForegroundInactive -> 1
                else -> 2
            }
        }
        val window = ranked.firstNotNullOfOrNull { scene ->
            val windows = scene.windows.mapNotNull { it as? UIWindow }
            windows.firstOrNull { it.isKeyWindow() } ?: windows.firstOrNull()
        } ?: return null
        var controller = window.rootViewController ?: return null
        while (true) {
            controller = when {
                controller.presentedViewController != null -> controller.presentedViewController!!
                controller is UINavigationController -> controller.visibleViewController ?: return controller
                controller is UITabBarController -> controller.selectedViewController ?: return controller
                else -> return controller
            }
        }
    }
}

internal actual class ShareService(private val host: UIViewController) {

    actual fun shareText(text: String, subject: String) {
        present(listOf(text))
    }

    actual fun shareFile(bytes: ByteArray, fileName: String, mimeType: String) {
        val path = NSTemporaryDirectory() + fileName
        val data = bytes.usePinned { pinned -> NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong()) }
        data.writeToFile(path, atomically = true)
        present(listOf(NSURL.fileURLWithPath(path)))
    }

    private fun present(items: List<Any>) {
        val controller = UIActivityViewController(activityItems = items, applicationActivities = null)
        controller.popoverPresentationController?.sourceView = host.view
        host.presentViewController(controller, animated = true, completion = null)
    }
}

@Composable
internal actual fun rememberShareService(): ShareService {
    val host = LocalUIViewController.current
    return remember(host) { ShareService(host) }
}

@Composable
internal actual fun InspectorBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // The page sheet is dismissed by swipe or the toolbar button; there is no system back gesture.
}

internal actual fun decodeImageBitmap(bytes: ByteArray): ImageBitmap? =
    runCatching { Image.makeFromEncoded(bytes).toComposeImageBitmap() }.getOrNull()
