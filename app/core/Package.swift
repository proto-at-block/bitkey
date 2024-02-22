// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "core",
    products: [
        .library(name: "core", targets: ["core", "coreFFI"]),
    ],
    dependencies: [],
    targets: [
        .target(
            name: "core",
            dependencies: ["coreFFI"],
            path: "_build/rust/uniffi/swift",
            sources: ["core.swift"]
        ),
        .binaryTarget(name: "coreFFI", path: "_build/rust/ios/coreFFI.xcframework"),
    ]
)
