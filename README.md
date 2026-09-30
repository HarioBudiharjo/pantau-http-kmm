# PantauHTTP for Kotlin Multiplatform

An in-app HTTP(S) inspector for Kotlin Multiplatform — like Chucker, but for **Ktor, OkHttp and URLSession** at once, with one shared **Compose Multiplatform** UI on Android and iOS.

Use it from `commonMain`. No `expect`/`actual` in your app: the library ships the platform glue.

- **Capture**: a Ktor client plugin (pure common), an OkHttp interceptor (Android) and a `URLProtocol` engine (iOS, incl. Alamofire/custom sessions).
- **Inspect**: searchable list, status filter chips (2xx/3xx/4xx/5xx/failed), Overview / Request / Response, pretty-printed JSON, image previews, redirect hops, timing and sizes.
- **Share**: cURL, plain text or HAR 1.2.
- **Open it**: shake the device, tap the coalesced "recent activity" notification, or call `PantauHttp.present()`.
- **Safe by default**: `Authorization`, `Cookie`, `Set-Cookie` and `Proxy-Authorization` values are replaced with `••••••••` before storage; bodies are capped at 1 MiB per side.
- **Web dashboard**: push traffic from many devices to a zero-dependency Node dashboard on your Mac (`dashboard/`).

## Requirements

| | |
|---|---|
| Kotlin | 2.4.10 |
| Android | API 23+ for `pantau-http` (Compose UI), API 21+ for `pantau-http-core` |
| iOS | 15.0+ (Kotlin/Native 2.4 minimum), `iosArm64` and `iosSimulatorArm64` (Compose Multiplatform 1.11 has no x86_64) |
| Build | JDK 17, Gradle 9.1, Android Gradle Plugin 9.0, Xcode 16+ |

## Installation

### Kotlin Multiplatform / Android (Gradle)

Artifacts are served from a static Maven repository on GitHub Pages; no account or token is needed:

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://hariobudiharjo.github.io/pantau-http-kmm/maven")
    }
}
```

```kotlin
// commonMain
implementation("com.pantauhttp:pantau-http:2.0.1")        // capture + Compose UI
// or, headless (no Compose): capture, exports and dashboard push only
implementation("com.pantauhttp:pantau-http-core:2.0.1")
```

The same coordinates are also on [GitHub Packages](https://github.com/HarioBudiharjo?tab=packages&repo_name=pantau-http-kmm) (`https://maven.pkg.github.com/HarioBudiharjo/pantau-http-kmm`, which GitHub gates behind a token with `read:packages`), and `./gradlew publishToMavenLocal` puts them in `~/.m2` when building from source.

Consider keeping it out of release builds (`debugImplementation`, or a `BuildConfig.DEBUG` guard around `start()`).

### iOS (Swift Package Manager)

```swift
dependencies: [
    .package(url: "https://github.com/HarioBudiharjo/pantau-http-kmm.git", from: "2.0.1")
]
```

Link **exactly one** product: `PantauHTTP` (inspector UI + core) or `PantauHTTPCore` (headless). Both are prebuilt XCFrameworks attached to the [GitHub release](https://github.com/HarioBudiharjo/pantau-http-kmm/releases/latest); `Package.swift` pins their checksums. The Swift module is `PantauHTTP` (capitals, like the 1.x package) so it never shadows the `PantauHttp` entry-point class.

To build the frameworks yourself: `./gradlew :pantau-http:assemblePantauHTTPXCFramework` → `pantau-http/build/XCFrameworks/{debug,release}/PantauHTTP.xcframework`.

If your iOS app already embeds a Kotlin framework from your own shared module, depend on `com.pantauhttp:pantau-http` from that module instead and `export(...)` it, so there is still a single Kotlin runtime in the process.

## Quick start (commonMain)

```kotlin
import com.pantauhttp.PantauHttp
import com.pantauhttp.ktor.PantauHttpPlugin

// 1. Start once, early (before creating HTTP clients), in debug builds only.
PantauHttp.start()

// 2. Any Ktor client you want inspected:
val client = HttpClient { install(PantauHttpPlugin) }

// 3. Open the inspector (or shake the device).
PantauHttp.present()
```

Where "early" means `Application.onCreate` on Android and `application(_:didFinishLaunchingWithOptions:)` on iOS. On iOS the `URLProtocol` engine is registered for `URLSession.shared` and every session created afterwards from `URLSessionConfiguration.default`.

### Configuration

```kotlin
PantauHttp.start(
    PantauHttpConfiguration(
        maxTransactions = 200,                              // ring buffer size
        bodySizeLimit = 1_048_576,                          // bytes stored per body
        shakeEnabled = true,
        notificationPolicy = NotificationPolicy.WhenBackgrounded, // Never / WhenBackgrounded / Always
        redactedHeaders = setOf("Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization"),
        ignoredHosts = setOf("analytics.example.com"),
        dashboardUrl = "http://192.168.1.20:9435",          // optional, see Web dashboard
        dashboardDeviceName = "QA Phone 3",                 // optional
    ),
)
```

### Programmatic access

```kotlin
PantauHttp.transactions          // StateFlow<List<HttpTransaction>>, newest first
PantauHttp.unseenCount           // StateFlow<Int>
PantauHttpExports.curl(tx)       // also .text(tx) and .har(listOf(tx))
PantauHttp.clearTransactions()
```

`PantauHttpInspector(onClose = …)` is a public composable if you prefer to host the inspector inside your own navigation.

## Android

`PantauHttp.start()` needs no `Context`: a tiny `ContentProvider` captures the application context at process start. If you strip providers from the manifest or run in an isolated process, call `PantauHttpAndroid.init(context)` first.

Plain OkHttp, Retrofit and Coil clients are captured by the interceptor:

```kotlin
OkHttpClient.Builder()
    .addInterceptor(PantauHttpInterceptor())   // application interceptor: sees failures and the request as you built it
    .build()
```

Transactions complete when response headers arrive; the body is filled in as your code reads it, so an unread body never leaves a transaction in progress. A Ktor client built on such an OkHttp client is recorded once: the plugin marks its requests with `X-PantauHTTP-Trace` and the interceptor strips the header and passes them through. Add it as a network interceptor instead if you want each redirect hop as its own transaction.

Notifications require the host app to request `POST_NOTIFICATIONS` on API 33+; the library only posts when notifications are enabled.

## iOS (from Swift)

```swift
import PantauHTTP

// AppDelegate, before any URLSession is created
PantauHttp.shared.start(configuration: PantauHttpConfiguration())          // defaults
PantauHttp.shared.start(configuration: PantauHttpConfiguration(            // or explicit: 2.0.1 exposes the full
    maxTransactions: 200, bodySizeLimit: 1_048_576, shakeEnabled: true,   // initializer only (partial-argument
    notificationPolicy: .whenBackgrounded,                                 // overloads arrive in the next release)
    redactedHeaders: ["Authorization", "Cookie", "Set-Cookie", "Proxy-Authorization"],
    ignoredHosts: [], dashboardUrl: "http://192.168.1.20:9435", dashboardDeviceName: nil
))

// Custom sessions and Alamofire: inject the engine BEFORE creating the session
let configuration = URLSessionConfiguration.default
PantauHttpIos.shared.enable(configuration: configuration)
let session = Session(configuration: configuration)   // Alamofire

// If your app owns the UNUserNotificationCenter delegate, forward taps:
if PantauHttpIos.shared.handleNotification(response: response) { completionHandler(); return }
```

The framework is built with [SKIE](https://skie.touchlab.co), so `PantauHttp.shared.transactions` is an `AsyncSequence` and Kotlin default arguments are available in Swift.

Add `CADisableMinimumFrameDurationOnPhone = YES` to your app's `Info.plist` so the Compose inspector can render at the display's full refresh rate (the library does not require it, but Compose Multiplatform recommends it).

Limitations inherited from `URLProtocol` capture: per-session authentication and certificate-pinning delegates are bypassed for captured requests, and sessions created *before* `start()` from a default configuration are not captured.

## Web dashboard

```bash
node dashboard/server.js        # Node 18+, no npm install; prints the LAN URL to use
```

Set `dashboardUrl` to the printed address (`http://10.0.2.2:9435` from the Android emulator, `http://127.0.0.1:9435` from the iOS Simulator) and open `http://localhost:9435` in a browser. Every device shows up in the sidebar with live transactions, search, filters, cURL copy and HAR download. Headers are redacted and bodies capped before anything leaves the device. Real iOS devices need `NSAllowsLocalNetworking` and `NSLocalNetworkUsageDescription` in `Info.plist`; Android needs `usesCleartextTraffic` (debug builds) for plain-HTTP dashboards. See `dashboard/README.md`.

## Capture rules

- The Ktor plugin records the client it is installed in; install it **last** so redirects and retries show up as separate transactions.
- Native engines (OkHttp interceptor, iOS `URLProtocol`) skip requests carrying `X-PantauHTTP-Trace` (already recorded by Ktor) and `X-PantauHTTP-Internal` (the dashboard pusher).
- On iOS each redirect hop is its own transaction; with the OkHttp application interceptor the followed hops are listed under the final transaction's redirects.
- Header redaction is case-insensitive and applied before storage; the original value is never retained.

## Samples

- `example/`: a standalone app pair that consumes the **published** SDK (Gradle from the GitHub Pages repository, iOS via Swift Package Manager); the quickest way to see the integration a real app needs.

- `sample/androidApp`: `./gradlew :sample:androidApp:installDebug -PdashboardUrl=http://10.0.2.2:9435`, then `adb shell am start -n com.pantauhttp.sample/.MainActivity --ez autofire true`.
- `sample/iosApp`: `cd sample/iosApp && xcodegen generate && open PantauHttpSample.xcodeproj` (the pre-build script runs `embedAndSignAppleFrameworkForXcode`). Set `PANTAU_DASHBOARD_URL` and `AUTOFIRE=1` in the scheme's environment to script it.

## Building from source

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)   # Gradle 9.1 / AGP 9.0 do not run on newer JDKs
./gradlew :pantau-http-core:allTests :pantau-http:allTests
./gradlew :pantau-http:assemblePantauHTTPXCFramework
./gradlew publishToMavenLocal
```

## Migrating from 1.x (Swift)

| 1.x (Swift) | 2.0 |
|---|---|
| `PantauHTTP.start(with:)` | `PantauHttp.shared.start(configuration:)` |
| `PantauHTTP.enable(in:)` | `PantauHttpIos.shared.enable(configuration:)` |
| `PantauHTTP.present()` / `dismiss()` | `PantauHttp.shared.present()` / `dismiss()` |
| `PantauHTTP.handleNotification(_:)` | `PantauHttpIos.shared.handleNotification(response:)` |
| `Configuration.dashboardURL: URL?` | `dashboardUrl: String?` |
| SPM / CocoaPods source package | XCFramework built by Gradle |

## License

MIT — see `LICENSE`.
