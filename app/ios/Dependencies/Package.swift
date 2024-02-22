// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "Dependencies",
    platforms: [.iOS(.v15)],
    products: [
        .library(
            name: "Dependencies",
            targets: ["Dependencies"]
        ),
    ],
    dependencies: [
        .package(url: "https://github.com/bitcoindevkit/bdk-swift", exact: "0.31.0"),
        .package(url: "https://github.com/bugsnag/bugsnag-cocoa", exact: "6.28.0"),
        .package(url: "https://github.com/Datadog/dd-sdk-ios", exact: "2.7.0"),
        .package(url: "https://github.com/jurvis/ldk-node", exact: "0.0.4"),
        .package(url: "https://github.com/rickclephas/kmp-nativecoroutines", exact: "0.13.3"),
        .package(url: "https://github.com/marmelroy/PhoneNumberKit", exact: "3.7.6"),
        .package(url: "https://github.com/weichsel/ZIPFoundation", exact: "0.9.17"),
        .package(url: "https://github.com/airbnb/lottie-spm", exact: "4.3.4"),

        .package(name: "core", path: "../../core"),

    ],
    targets: [
        .target(
            name: "Dependencies",
            dependencies: [
                .product(name: "BitcoinDevKit", package: "bdk-swift"),
                .product(name: "Bugsnag", package: "bugsnag-cocoa"),
                .product(name: "DatadogCore", package: "dd-sdk-ios"),
                .product(name: "DatadogCrashReporting", package: "dd-sdk-ios"),
                .product(name: "DatadogLogs", package: "dd-sdk-ios"),
                .product(name: "DatadogRUM", package: "dd-sdk-ios"),
                .product(name: "DatadogTrace", package: "dd-sdk-ios"),
                .product(name: "LightningDevKitNode", package: "ldk-node"),
                .product(name: "Lottie", package: "lottie-spm"),
                .product(name: "KMPNativeCoroutinesCombine", package: "kmp-nativecoroutines"),
                .product(name: "PhoneNumberKit", package: "PhoneNumberKit"),
                .product(name: "ZIPFoundation", package: "ZIPFoundation"),

                .product(name: "core", package: "core"),
            ]
        ),
    ]
)
