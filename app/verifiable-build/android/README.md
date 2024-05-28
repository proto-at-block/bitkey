# Verifiable builds for Android

This directory contains scripts needed for building reproducible builds and their consecutive verification.

The `release` directory contains scripts primarily used in CI for producing release builds.

The `verification` directory contains `verify-android-apk` which automates the full verification process - from downloading the APK from the phone, building the local version, to comparing the results.
The directory also contains `steps` directory which holds scripts for each of the verification steps.

> [!NOTE]
> To run the verification build,
> you need either an x86_64 machine.
> So either one of:
>
> - Intel macOS
> - x86_64 Linux distribution
> - x86_64 Windows installation with WSL2
>
> Once Google adds linux/arm64 support for Android SDK,
> we'll update our scripts to support running on Apple Silicon macOS.
> This limitation stems from the `aapt2` binary crashing
> when emulated using Apple's Rosetta 2.

## Verifying on your machine

### Requirements

You'll need:

- an x86_64 machine:
  - Intel macOS,
  - x86_64 Linux distribution,
  - or x86_64 Windows installation with WSL2
- Android SDK with ANDROID_HOME set
- Docker
- an Android phone with Bitkey installed
- experience with terminal

### Prep work

1. Clone the bitkey repository
  
    ```sh
    git clone https://github.com/proto-at-block/bitkey.git
    cd bitkey
    git submodule update --init --recursive
    ```

2. Activate Hermit

    ```sh
    source bin/activate-hermit
    ```

3. Download bundletool

    ```sh
    curl -L -o bundletool.jar https://github.com/google/bundletool/releases/download/1.15.6/bundletool-all-1.15.6.jar
    export BUNDLETOOL="$(pwd)/bundletool.jar"
    ```

4. Export AAPT2

    ```sh
    export AAPT2="$ANDROID_HOME/build-tools/34.0.0
    ```

5. Connect your phone using USB and make sure it's authorized for USB debugging

### Running verification

The verification script `app/verifiable-build/android/verification/verify-android-apk` takes two parameters:
- Bitkey repository path
- Work directory to perform verification in

Assuming you followed the commands in [Prep work](#prep-work),
you should be inside the `bitkey` directory containing the sources.
To begin verification, run the script like so:

```sh
app/verifiable-build/android/verification/verify-android-apk . verify-apk
```

Once the script finishes,
look through its output.
It should end with builds being identical.

> [!IMPORTANT]
> Depending on your phone's locale,
> the verification may fail
> due to a mismatch between `bundletool` and **Google Play** behaviors.
>
> If the verification fails, navigate to `verify-apk/from-device-downloaded`.
> In there you should see multiple `.apk` files.
> Notice files named like `split_config.en.apk`, `split_config.fr.apk` etc.
> These files contain localized resources for the language `en`, `fr` etc.
>
> Then navigate to `verify-apk/locally-built/apks` and note any missing languages.
> Once you find missing languages, go to your phone's settings.
> Add all the languages that were missing as secondary languages.
> 
> Once added, rerun verification.
> If the verification still fails,
> please reach out to us.

## Verification notes

### `bundletool` vs **Google Play** mismatch

Although Google Play should be using `bundletool`,
APKs extracted from an AAB differ in more than just a signature.

As mentioned above,
we noticed a mismatch between language APKs downloaded by **Google Play**
and those produced by `bundletool`.
We use the `--connected-device` flag when invoking `bundletool build-apks`,
which should produce the same set of APKs.
However,
looking at the output of `bundletool get-device-spec`,
the `supportedLocales` list is often missing some languages that **Google Play** downloaded to the device.

Another such difference are names of extracted APKs.
We use the `normalize-apk-names-new` script to rename APK files to matching names.

### `resources.arsc` mismatch

Until recently,
once we normalized the APK names and contents,
we could just run `diff -r` to check for identity.
Unfortunately **Google Play** has changed how they build `resources.arsc`.
From our testing,
it seems like they are using a [previously reserved byte](https://android.googlesource.com/platform/frameworks/base/+/master/libs/androidfw/include/androidfw/ResourceTypes.h#1405).
When built using `bundletool`, that byte is always `0`,
thus making direct comparison using `diff` impossible.

Since resources are important part of the application,
we're using `aapt2 diff` to check for differences between APKs from device and from `bundletool`.
