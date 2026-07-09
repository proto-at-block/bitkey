# JVM Integration Test Shards

Owner: Mobile
Last reviewed: 2026-05-22

This directory owns the non-isolated JVM integration test shard map used by the app and server GitHub Actions workflows.

The executable supports three commands:

```bash
scripts/jvm-integration-test-shards/run list
scripts/jvm-integration-test-shards/run validate
scripts/jvm-integration-test-shards/run args <shard>
```

Supported shards:

- `account-recovery`
- `social-recovery`
- `wallet-transactions`
- `onboarding-upgrade-misc`

## How CI Uses It

The app and server workflows call `_app-shared-gradle.yml` with `integration-test-shard` set to one of the supported shard names.

When that input is set, the reusable workflow:

1. Runs `scripts/jvm-integration-test-shards/run validate`.
2. Resolves the shard to a Gradle argument line with `scripts/jvm-integration-test-shards/run args <shard>`.
3. Runs those generated arguments instead of the raw `gradle-targets` input.

The generated arguments include:

- Targeted `:module:jvmIntegrationTest` tasks for modules that contain tests in the shard.
- `-Dkotest.tags=!IsolatedTest`.
- `-Pbitkey.integrationTest.shard=<shard>`.
- Exact `--tests <FQCN>` filters for every class assigned to the shard.

## Discovery And Validation

`validate` automatically discovers JVM integration test classes in Gradle modules declared by `settings.gradle.kts` when files live under `src/jvmIntegrationTest/kotlin/**/*.kt`.

Discovered test classes include concrete classes that directly extend Kotest specs such as `FunSpec`, `BehaviorSpec`, or `StringSpec`. Classes that extend a project wrapper around a Kotest spec are also discovered when the class name matches the Kotlin file stem and ends in `Test` or `Tests`.

Every discovered class must map to exactly one semantic shard. Validation fails for unassigned discovered classes, duplicate assignments, and shard assignments that no longer match a discovered class. This prevents new non-isolated integration tests from silently being skipped by the sharded CI lane.

`validate` also checks that the shard matrices in `.github/workflows/app.yml` and `.github/workflows/server.yml` exactly match the shard names in `SHARDS`. When adding, removing, or renaming a shard, update both workflow matrices in the same change.

## Adding Or Moving Tests

To add a test:

1. Add the JVM integration test class under a module's `src/jvmIntegrationTest/kotlin` source tree.
2. Choose the semantic shard that best matches the class.
3. Add the class FQCN to exactly one list in `SHARDS` inside `scripts/jvm-integration-test-shards/run`.
4. Run:

```bash
scripts/jvm-integration-test-shards/run validate
gradle --console=plain --dry-run $(scripts/jvm-integration-test-shards/run args <shard>)
```

Prefer moving whole test classes between shards. Do not split one class across shards.

## Shard Boundaries

Use these boundaries when assigning new tests:

- `account-recovery`: lost app, lost hardware, emergency exit, lite recovery, and related data-state-machine recovery tests.
- `social-recovery`: social recovery, trusted contacts, inheritance, relationships, and recovery challenge tests.
- `wallet-transactions`: wallet domain tests plus UI send, transactions, UTXO, export, mobile pay, treasury, and sweep tests.
- `onboarding-upgrade-misc`: onboarding/create flows, W3/private wallet upgrades, cloud backup recovery flows, F8e/client component tests, app/cloud/connectivity/firmware update/settings/security center/partnerships/misc tests.

Shard balancing is semantic and manual for now. Automatic assignment can be explored later if maintaining the shard map becomes noisy.

## Required Checks

The sharded jobs have per-shard names so failures show the responsible shard. Separate aggregate jobs preserve the historical required check names:

- `Integration Tests / gradle`
- `Mobile Integration Tests / gradle`

The aggregate job succeeds only when every shard in its matrix succeeds.

## Notes

The isolated integration lane is intentionally separate and still runs with:

```bash
jvmIntegrationTest -Dkotest.tags=IsolatedTest --max-workers=1 -Pbitkey.integrationTest.maxParallelForks=1
```

The flake-finder workflow is intentionally unsharded for the initial rollout.
