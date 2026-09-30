// swift-tools-version: 5.9
// Binary distribution of the Kotlin Multiplatform PantauHTTP frameworks.
// Link exactly one product: PantauHTTP (inspector UI + core) or PantauHTTPCore (headless).
import PackageDescription

let package = Package(
    name: "PantauHTTP",
    platforms: [
        .iOS(.v15)
    ],
    products: [
        .library(name: "PantauHTTP", targets: ["PantauHTTP"]),
        .library(name: "PantauHTTPCore", targets: ["PantauHTTPCore"]),
    ],
    targets: [
        .binaryTarget(
            name: "PantauHTTP",
            url: "https://github.com/HarioBudiharjo/pantau-http-kmm/releases/download/v2.0.0/PantauHTTP.xcframework.zip",
            checksum: "7ba3486e13971059e39f6580d59e46a41c19129f4277bb143600f1a16ea59cfd"
        ),
        .binaryTarget(
            name: "PantauHTTPCore",
            url: "https://github.com/HarioBudiharjo/pantau-http-kmm/releases/download/v2.0.0/PantauHTTPCore.xcframework.zip",
            checksum: "d84c418c475303574b5201daecd86f0b34c827bbd101e481c2c630f99042d8bd"
        ),
    ]
)
