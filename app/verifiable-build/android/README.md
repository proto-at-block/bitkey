# Verifiable builds for Android

This directory contains scripts needed for building reproducible builds and their consecutive verification.

The `release` directory contains scripts primarily used in CI for producing release builds.

The `verification` directory contains `verify-android-apk` which automates the full verification process - from downloading the APK from the phone, building the local version, to comparing the results.
The directory also contains `steps` directory which holds scripts for each of the verification steps. 

General documentation can be found here: `docs/docs/guides/mobile/build/verifiable-builds/verifiable-builds-android.md`

