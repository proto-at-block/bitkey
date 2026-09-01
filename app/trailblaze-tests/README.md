# Trailblaze UI Tests

End-to-end Android UI tests for the Bitkey app, built on [Trailblaze](https://github.com/block/trailblaze)
(Block's AI-assisted UI testing framework). Tests are authored once with AI assistance, then
recorded as `.trail.yaml` files and **replayed deterministically — no LLM, no API key, no
per-run cost** — as standard instrumented tests on an Android emulator.

## ⚠️ This is a standalone Gradle build — on purpose

This directory has its own `settings.gradle.kts` and is **not** a module of the main app build.
Do not register it in `app/settings.gradle.kts`, and do not "clean it up" by merging it into the
main build. That was attempted and abandoned. Here is the full story so you don't re-derive it:

### Why it can't live in the main app build

The main build uses a **custom dependency-locking system** (`app/gradle/dependency-locking/`,
wired in by every `build.wallet.*` convention plugin) that exists for verifiable builds /
supply-chain reproducibility:

1. Every configuration of every module is assigned to a named **lock group**
   (mostly `build-classpath`), recorded with artifact hashes in `app/gradle-lock/`.
2. The core invariant: within a lock group, **every configuration across every module must
   resolve the identical version** of any given dependency. One coherent dependency universe.
3. To hold that invariant, build-logic injects ~100 **`strictly` version pins**
   (kotlin-stdlib, kotlinx-datetime 0.6.1, coroutines, …) into every module's classpaths
   (`DependencyLockingDependencyConfigurationPlugin`).

Trailblaze + koog is a **second dependency universe**: Kotlin 2.3.x metadata, ktor 3.x,
kotlinx-datetime 0.7.x-compat. Inside the main build this collides with the invariant in layer
after layer — the strict pins conflict with trailblaze's requirements, pins leak into the test
classpath through the module's own consumable variants, stripping pins breaks cross-module
group unification, and compile-vs-runtime drift breaks within-group unification. Every fix
surfaces the next violation, because the system is *designed* to forbid exactly this.

The test APK depends on **zero app modules** — it drives the separately installed debug app
over the accessibility APIs — so there is no actual need to share a build with the app. A
standalone build gives trailblaze its own dependency universe and leaves the main build's
locking pristine.

### Build relationship to the main build

- Runs on the same hermit-provided Gradle (run from `app/` with hermit activated).
- AGP version intentionally matches the main build's (`gradle/libs.versions.toml`
  `android-gradle-plugin`) so both work on the same Gradle.
- Kotlin version intentionally matches **trailblaze's** build (its artifacts carry Kotlin 2.3.x
  metadata that the main build's older Kotlin compiler cannot consume).
- `compileSdk` is dictated by trailblaze's `quickjs-kt` dependency (currently 36), not by the
  main app's `android-sdk-compile`.

## Building and running

```bash
cd app && . bin/activate-hermit
gradle -p trailblaze-tests assembleDebugAndroidTest        # build test APK
gradle -p trailblaze-tests --no-configuration-cache connectedDebugAndroidTest \
  -Pandroid.aapt2FromMavenOverride="$ANDROID_HOME/build-tools/36.0.0/aapt2" \
  -Pandroid.testInstrumentationRunnerArguments.trailblaze.aiEnabled=false   # replay on emulator
```

Prerequisites for a run:
1. An emulator/device with the **debug app installed** (`world.bitkey.debug` — build with
   `gradle :android:app:assembleDebug` from the main build, then `adb install`). The debug app
   defaults to Staging F8e + SIGNET + fake hardware, which is what the trails assume.
2. Android build tools 36.0.0 installed, or `AAPT2` set to an installed `aapt2` binary.
3. `trailblaze.aiEnabled=false` makes replay recordings-only: it fails loudly instead of asking
   an LLM to self-heal. CI must always set this. No LLM key is needed for replay.

## Vendored trailblaze artifacts (read before bumping trailblaze)

Trailblaze artifacts resolve from `vendor/maven/` (checked into git), built from the
`opensource/` subtree of `squareup/trailblaze-internal` at a pinned commit. Public dependencies
resolve from the standard repositories (`google()` / `mavenCentral()`); only the
`xyz.block.trailblaze` group is allowed to resolve from the local vendor repository.

Integrity is enforced by **Gradle dependency verification** (`gradle/verification-metadata.xml`,
SHA-256 per artifact — including the vendored ones), not by routing through an internal mirror.
This keeps builds hermetic and tamper-evident: every artifact is pinned to exact bytes, so a
dependency-confusion / artifact-shadowing attack fails verification, and a transient outage of
Block's Artifactory can no longer block the build. (This build deliberately has no part in the
main app's `gradle-lock` convention — see the top of this file — so it uses Gradle's native
verification instead.) Regenerate the metadata whenever dependency versions change; see the bump
steps below.

**Why not Maven Central?** Two reasons, both verified the hard way:

1. **The published artifacts are broken for on-device use.** Trailblaze's KMP modules
   (`trailblaze-common`, `trailblaze-models`, `trailblaze-quickjs-tools`) never call
   `publishLibraryVariants()` on their `androidTarget`, so their **Android variants are never
   published** — only `-jvm`. An Android test APK then dexes the jvm jars and crashes at
   runtime with `NoClassDefFoundError` on androidMain-only classes (first symptom:
   `xyz.block.trailblaze.AdbCommandUtil` during accessibility-service setup). Trailblaze's own
   repo consumes these modules as local `project(...)` dependencies, which masks the bug
   upstream.
2. **Snapshots are retention-limited** (~90 days on Maven Central snapshots) and trailblaze
   has no stable releases yet, so even working remote artifacts could age out from under CI.

Current provenance is recorded in `vendor/trailblaze-provenance.properties` and checksums are
recorded in `vendor/SHA256SUMS`. Validate both with:

```bash
vendor/verify-vendored-trailblaze.sh
```

To bump trailblaze:

1. Resolve the internal tag to a commit SHA:
   `git ls-remote git@github.com:squareup/trailblaze-internal.git 'refs/tags/v<version>^{}'`
   (for lightweight tags, omit `^{}`).
2. Update `vendor/trailblaze-provenance.properties` with the new tag, version, commit, and
   source path.
3. Update `trailblazeVersion` in `build.gradle.kts` and re-sync `koogVersion`/`ktor3Version`
   with trailblaze's `opensource/gradle/libs.versions.toml` at that commit.
4. Run `vendor/update-vendored-trailblaze.sh`. It verifies the remote tag still points to the
   pinned commit, checks out that commit, applies the two required patches (disable publication
   signing; add `publishLibraryVariants`), publishes to maven-local, refreshes `vendor/maven/`,
   strips local Maven metadata/source jars, regenerates `vendor/SHA256SUMS`, and runs the
   verifier.
5. Regenerate `gradle/verification-metadata.xml` so the new artifact checksums are recorded.
   **Generate from a clean cache** — a warm cache silently omits parent POMs / BOMs:
   ```bash
   GEN_HOME="$(mktemp -d)"
   GRADLE_USER_HOME="$GEN_HOME" gradle -p trailblaze-tests \
     --write-verification-metadata sha256 --no-configuration-cache assembleDebugAndroidTest
   rm -rf "$GEN_HOME"
   ```
   Then confirm completeness with another clean cache (this build must succeed — it fails if any
   artifact lacks a checksum):
   ```bash
   CLEAN_HOME="$(mktemp -d)"
   GRADLE_USER_HOME="$CLEAN_HOME" gradle -p trailblaze-tests \
     --no-configuration-cache assembleDebugAndroidTest
   rm -rf "$CLEAN_HOME"
   ```
6. Run the Gradle checks from this README before opening a PR.

When upstream fixes its Android-variant publishing and ships stable releases, delete
`vendor/` and resolve the public coordinates from `mavenCentral()` (still covered by
`gradle/verification-metadata.xml`).

## Layout

```
trails/                                  # bundled into the test APK as assets
  config/trailblaze.yaml                 # LLM defaults for AUTHORING sessions only
  bitkey/<area>/<C-testrail-id-or-slug>/
    blaze.yaml                           # natural-language source (portable, re-recordable)
    android-phone.trail.yaml             # deterministic recording replayed in CI
src/androidTest/.../SmokeTrails.kt       # JUnit classes: rule.runFromAsset("<path under trails/>")
```

Trail authoring workflow (TestRail case → recorded trail): see
`docs/docs/mobile/testing/trailblaze.md`.
