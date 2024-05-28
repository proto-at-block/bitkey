// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "rust",
    products: [
        .library(name: "core", targets: ["core", "coreFFI"]),
        .library(name: "firmware", targets: ["firmware", "firmwareFFI"]),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "core",
            dependencies: ["coreFFI"],
            path: "_build/rust/uniffi/swift",
            sources: ["core.swift"]
        ),
        .target(
            name: "firmware",
            dependencies: ["firmwareFFI"],
            path: "_build/rust/uniffi/swift",
            sources: ["firmware.swift"]
        ),
        .binaryTarget(name: "coreFFI", path: "_build/rust/ios/coreFFI.xcframework"),
        .binaryTarget(name: "firmwareFFI", path: "_build/rust/ios/firmwareFFI.xcframework"),
    ]
)
