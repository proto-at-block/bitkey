# Getting Started

## Environment

Execute the following steps in order to prepare your app development environment:

  - Install the latest version of Xcode from [Apple](https://developer.apple.com/download/all/?q=xcode)

  # install-git-lfs install-xcode-clt install-jetbrains-toolbox submodules install-xcode-kmp-plugin
  - Run `just install-git-lfs`
  - Run `just install-xcode-clt`
  - Run `just install-jetbrains-toolbox`
  - Run `just submodules`
  - Run `just install-xcode-kmp-plugin`
  - Execute `just list-env-vars` and append the output to your `~/.zshrc`. Close and re-open your terminal to pick up the new environment variables.
  - Run `java --version`: This installs the needed Java tooling.

## Code Formatting

We use [Ktlint](https://github.com/pinterest/ktlint) to enforce consistent code formatting across the codebase.
The rules configuration is defined in `app/.editorconfig`.

It's recommended to install the Ktlint [IDE plugin](https://plugins.jetbrains.com/plugin/15057-ktlint) to ensure that your IDE formats the code using `.editorconfig` and `ktlint` rules.

## Android app and KMP

We employ IntelliJ IDEA <del>Android Studio (AS)</del> as our primary IDE for Android app development and managing the shared Kotlin Multiplatform codebase.

We recommend managing IntelliJ-based IDEs with Jetbrains Toolbox, which should be installed as part of the `just install-jetbrains-toolbox`.

### IntelliJ IDEA <del>Android Studio</del>

Follow these steps:

1. Install **IntelliJ IDEA Community Edition 2023.3** via JetBrains Toolbox.
1. Boost IDE performance by tweaking VM options:
    - Launch the JetBrains Toolbox app.
    - Access the settings for IntelliJ IDEA.
    - Set the max heap size to 20,000 MB (feel free to adjust as per your requirements).
1. Start the IntelliJ IDEA without loading a project.
1. Install the following plugins: `Android`, `Hermit`, `Kotest`, `Ktlint`, `detekt`.
1. Install the Android SDK that aligns with the `android-sdk-compile` version as specified in `gradle/libs.versions.toml`.
1. Similarly, install the Android NDK that matches the `android-ndk` version in `gradle/libs.versions.toml`.
1. Restart the IDE and navigate to the `app/` directory.
1. Initiate a Gradle Sync. Note: The initial sync might be time-consuming.
1. Your IDE setup is ready to go! 🎉

### Building the Android app

- First, set up an [Android emulator](https://developer.android.com/studio/run/emulator) or configure a real device in [development mode](https://developer.android.com/studio/debug/dev-options).

For building:

- Inside the IDE, select the `android:app` configuration and hit "Run".
- Alternatively, from the command line, execute: `just android-app-install`.

## iOS app

### Xcode

1. Begin by installing Xcode and its command-line tools.
1. Generate the Xcode project by executing `just xcodegen`. This may be time-intensive initially. n.b. Make sure `xcode-select -p` prints out the path to the Developer directory of where the Xcode app is installed
1. Proceed by opening the Xcode project using `open ios/Wallet.xcodeproj`.
