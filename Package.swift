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
            url: "https://github.com/HarioBudiharjo/pantau-http-kmm/releases/download/v2.0.1/PantauHTTP.xcframework.zip",
            checksum: "55ca8c41972989e9fc67914c1418c03e51f86dda4f71e56bef8b14adc84e648f"
        ),
        .binaryTarget(
            name: "PantauHTTPCore",
            url: "https://github.com/HarioBudiharjo/pantau-http-kmm/releases/download/v2.0.1/PantauHTTPCore.xcframework.zip",
            checksum: "6e9b6f2d98e1c96c4958391088a0e8b7faa1f6e7aae0b41dcf7d3f0cebbe441e"
        ),
    ]
)
