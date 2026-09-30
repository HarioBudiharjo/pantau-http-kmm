package com.pantauhttp

import com.pantauhttp.internal.capture.PANTAU_MARKER_KEY
import com.pantauhttp.internal.capture.PantauUrlProtocol
import com.pantauhttp.internal.capture.SessionInjector
import com.pantauhttp.internal.remote.currentDeviceIdentity
import com.pantauhttp.ktor.PANTAU_INTERNAL_HEADER
import com.pantauhttp.shim.PantauShakeInstall
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLProtocol
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSessionConfiguration
import platform.Foundation.setValue
import platform.Foundation.URL
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class IosEngineTest {

    private fun request(url: String, configure: NSMutableURLRequest.() -> Unit = {}): NSURLRequest =
        NSMutableURLRequest(NSURL(string = url)).apply(configure)

    private val quiet = PantauHttpConfiguration(shakeEnabled = false, notificationPolicy = NotificationPolicy.Never)

    @AfterTest
    fun tearDown() {
        PantauHttpCore.stop()
        PantauHttpCore.clearTransactions()
    }

    @Test
    fun canInitGateReachesKotlinThroughTheShim() {
        PantauHttpCore.installGateForTests()
        PantauHttpCore.stop()
        assertFalse(PantauUrlProtocol.canInitWithRequest(request("https://example.com/")), "not started")

        PantauHttpCore.start(quiet)
        assertTrue(PantauUrlProtocol.canInitWithRequest(request("https://example.com/")))
        assertTrue(PantauUrlProtocol.canInitWithRequest(request("http://example.com/")))
        assertFalse(PantauUrlProtocol.canInitWithRequest(request("ftp://example.com/")), "non-http scheme")
        assertFalse(
            PantauUrlProtocol.canInitWithRequest(request("https://example.com/") { setValue("1", forHTTPHeaderField = PANTAU_INTERNAL_HEADER) }),
            "internal traffic",
        )
        val marked = NSMutableURLRequest(NSURL(string = "https://example.com/"))
        NSURLProtocol.setProperty(true, forKey = PANTAU_MARKER_KEY, inRequest = marked)
        assertFalse(PantauUrlProtocol.canInitWithRequest(marked), "already handled")

        PantauHttpCore.start(quiet.copy(ignoredHosts = setOf("Analytics.Example.com")))
        assertFalse(PantauUrlProtocol.canInitWithRequest(request("https://analytics.example.com/t")))
        assertTrue(PantauUrlProtocol.canInitWithRequest(request("https://api.example.com/t")))
    }

    @Test
    fun canonicalRequestIsIdentity() {
        val req = request("https://example.com/x")
        assertEquals(req.URL?.absoluteString, PantauUrlProtocol.canonicalRequestForRequest(req).URL?.absoluteString)
    }

    @Test
    fun sessionInjectorIsIdempotent() {
        val configuration = NSURLSessionConfiguration.defaultSessionConfiguration()
        val before = configuration.protocolClasses.orEmpty().size
        SessionInjector.enable(configuration)
        SessionInjector.enable(configuration)
        assertEquals(before + 1, configuration.protocolClasses.orEmpty().size)
        assertTrue(SessionInjector.isEnabled(configuration))
    }

    @Test
    fun shakeInstallTwiceDoesNotCrash() {
        PantauShakeInstall { }
        PantauShakeInstall { }
        PantauShakeInstall(null)
    }

    @Test
    fun deviceIdentityIsStableLowercaseAndSimulator() {
        val first = currentDeviceIdentity(null)
        val second = currentDeviceIdentity(null)
        assertTrue(first.id.isNotEmpty())
        assertEquals(first.id, second.id)
        assertEquals(first.id.lowercase(), first.id)
        assertTrue(first.model.isNotEmpty())
        assertTrue(first.systemVersion.isNotEmpty())
        assertTrue(first.isSimulator, "unit tests run in the simulator")
        assertEquals("QA Phone 3", currentDeviceIdentity("QA Phone 3").name)
    }
}
