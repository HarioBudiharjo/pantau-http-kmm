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
            url: "https://github.com/HarioBudiharjo/pantau-http-kmm/releases/download/v2.0.2/PantauHTTP.xcframework.zip",
            checksum: "1c4ae0eac501b2951cacffa86989e1f4f22394e35ad4788c01dc772646ada1a9"
        ),
        .binaryTarget(
            name: "PantauHTTPCore",
            url: "https://github.com/HarioBudiharjo/pantau-http-kmm/releases/download/v2.0.2/PantauHTTPCore.xcframework.zip",
            checksum: "d8675883ed373e282c7440bcd2279857b79a9523a6e0acd7f7d25700a49e8a59"
        ),
    ]
)
