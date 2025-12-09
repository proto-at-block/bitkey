// swift-tools-version:5.5
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "bdk-swift-legacy",
    platforms: [
        .macOS(.v12),
        .iOS(.v15)
    ],
    products: [
        .library(
            name: "BitcoinDevKitLegacy",
            targets: ["bdkFFILegacy", "BitcoinDevKitLegacy"]),
    ],
    dependencies: [],
    targets: [
        .binaryTarget(
            name: "bdkFFILegacy",
            path: "Artifacts/bdkFFILegacy.xcframework"),
        .target(
            name: "BitcoinDevKitLegacy",
            dependencies: ["bdkFFILegacy"])
    ]
)
