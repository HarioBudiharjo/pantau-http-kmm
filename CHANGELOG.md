# Changelog

## 2.0.1

### Fixed
- OkHttp interceptor: transactions now complete when response headers arrive and the body is
  patched in as the app reads it, so a body that is never read or closed no longer stays
  "in progress" forever.
- Ktor plugin: streamed request bodies (`WriteChannelContent` / `ReadChannelContent` without a
  known length, or larger than `bodySizeLimit`) are tee'd instead of skipped: the first
  `bodySizeLimit` bytes are kept, the real size is counted, memory stays bounded.

### Added
- Token-free Maven repository on GitHub Pages (`https://hariobudiharjo.github.io/pantau-http-kmm/maven`)
  next to GitHub Packages.

## 2.0.0 — Kotlin Multiplatform

PantauHTTP is now a Kotlin Multiplatform library with a shared Compose Multiplatform UI.
It replaces the Swift-only 1.x line; the public API is new.

### Added
- `PantauHttpPlugin`: a Ktor client plugin that records every request/response of the
  client it is installed in, from `commonMain`, with no platform code in the app.
- `PantauHttpInterceptor`: an OkHttp interceptor for Retrofit, Coil and plain OkHttp clients.
- URLSession capture on iOS (`URLProtocol`), including custom sessions via
  `PantauHttpIos.enable(configuration:)`.
- Shared Compose Multiplatform inspector: searchable list, status filter chips, detail
  screen with Overview / Request / Response, pretty-printed JSON, image previews, and
  sharing as cURL, plain text or HAR 1.2.
- Programmatic access: `PantauHttp.transactions` (`StateFlow`, an `AsyncSequence` in Swift).
- Dashboard pusher (`dashboardUrl`, `dashboardDeviceName`) with the zero-dependency
  Node dashboard in `dashboard/`; the wire format is unchanged from 1.x.
- `pantau-http-core`: a headless artifact without Compose for apps that only need
  capture, exports and the dashboard.

### Changed
- Requirements: Android 5.0 (API 21) for `pantau-http-core`, Android 6.0 (API 23) for the
  Compose UI module, iOS 15.0+ (Kotlin/Native 2.4 minimum). Intel simulators (`iosX64`) are not built because
  Compose Multiplatform 1.11 dropped x86_64.
- iOS distribution is an XCFramework produced by Gradle instead of SPM/CocoaPods sources; the Swift module stays `PantauHTTP`.

## 1.0.1 (Swift)
- Fix: scope the shake swizzle to `UIResponder` to prevent an unrecognized-selector crash.

## 1.0.0 (Swift)
- Initial release: Chucker-style HTTP inspector for iOS.
