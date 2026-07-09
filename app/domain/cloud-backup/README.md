This module contains domain specific components for creating and recovering Keybox from customer's Cloud Storage.

Cloud Storage access is abstracted away and managed by `:libs:cloud-store` components.

## Cloud Backup Versioning

### Core Principles

1. **Immutable Versions**: Never modify existing backup data classes (`CloudBackupV2`, `CloudBackupV3`, etc.). Each
   version is a
   complete, immutable snapshot.

2. **Forward Compatibility**: New app versions must support reading all previous backup versions.

3. **Migration is not (currently) automatic**: Consider whether existing backups must be updated to the newest version, and if so evaluate automation such as via `CloudBackupHealthRepository`

### Creating a New Backup Version

When schema changes are needed:

1. Create a new data class (e.g., `CloudBackupV4`) implementing `CloudBackup`
2. Update `CloudBackupService`, `CloudBackupDao` to support daisy chain decoding
3. Create a version-specific restorer (e.g., `CloudBackupV4Restorer`)
4. Update the very many exhaustive `when` statements throughout the app
5. Update cloud backup creators e.g. `FullAccountCloudBackupCreator` to create the new format
6. Update the `isLatestVersion` extension function in `CloudBackup.kt` to return `false` for the old version and `true`
   for the new version
7. Consider whether automatic migration to the newest version is needed (see `CloudBackupVersionMigrationWorker`)

### Reading Backups (Daisy Chain Pattern)

Json strings are decoded into CloudBackups in multiple places. See `CloudBackupService` and `CloudBackupDao`. 

Implementations should daisy-chain decode, starting with the most recent version:

```kotlin
// V2 was the latest
return Json.decodeFromStringResult<CloudBackupV2>(backupEncoded)

// After adding V3, try V3 first then fallback to V2
return Json.decodeFromStringResult<CloudBackupV3>(backupEncoded)
   .orElse { Json.decodeFromStringResult<CloudBackupV2>(backupEncoded) }
   .mapError { ... }
```

### Testing Requirements
- Test reading each backup version
- Test migration paths between versions
- Test restoration from each version. See `CloudBackupV2RestorerImplTests` and `CloudBackupV3RestorerImplTests` for
  examples.

### CloudBackupV3 Changes

CloudBackupV3 adds the following fields to CloudBackupV2:

- `deviceNickname: String?` - Optional device identifier/nickname
- `createdAt: Instant` - Timestamp of when the backup was created

## CloudKit migration (iOS)

When `IosCloudKitBackupFeatureFlag` is enabled, we populate CloudKit with backup data for
existing accounts on iOS. Active backup migration creates a fresh backup using the latest
schema when CloudKit is missing the active account backup. Same-account CloudKit drift is
reconciled from KVS when KVS has a fresher same-account V3 backup. In addition, archived backup
keys already present in iCloud KVS are copied to CloudKit on a best-effort basis.

### Migration flow

```mermaid
flowchart TD
    A[Worker starts] --> B{Emergency variant?}
    B -->|Yes| Z[Skip]
    B -->|No| C{Flag enabled?}
    C -->|No| C1[Clear local reconciliation marker]
    C1 --> Z
    C -->|Yes| D{Active account?}
    D -->|No| Z
    D -->|Yes| E{iCloud signed in?}
    E -->|No| Z
    E -->|Yes| F[Best-effort copy archived KVS keys to CloudKit]
    F --> G{Already reconciled for account + iCloud identity?}
    G -->|Yes| G1{CloudKit missing or same-account KVS is fresher?}
    G1 -->|No| Z
    G1 -->|Yes| H
    G -->|No| H
    H{Same-account CloudKit backup?} -->|No| I[Create fresh backup]
    H -->|Yes| I1{KVS candidate is fresher?}
    I1 -->|No| K[Set reconciliation marker]
    I1 -->|Yes| J[Write fresher KVS backup and verify CloudKit]
    I --> J1[Write fresh backup and verify CloudKit]
    J1 --> K
    J --> K
```

- Runs at app startup and when the CloudKit feature flag changes.
- Active-backup reconciliation runs once per local flag-enable cycle for the active account and
  iCloud identity. When the flag is observed off, the local reconciliation marker is cleared so a
  later re-enable reconciles again.
- Only runs when there is an active account and a signed-in iCloud account.
- Reads CloudKit directly, without KVS fallback, to decide whether the active CloudKit backup is
  missing or stale.
- Uses `CloudBackupV3.createdAt` as the freshness signal for same-account CloudKit/KVS
  reconciliation. KVS only beats CloudKit when it is strictly fresher; equal timestamps keep
  CloudKit.
- First best-effort copies historical archived backup keys from iCloud KVS into CloudKit when missing there
  (for example `cb-<account-id>-<timestamp>` and `cloud-backup-<timestamp>`).
- Skips if CloudKit already has the freshest active backup for the active account.
- If CloudKit is missing, malformed, or only has an active backup for a different account ID,
  uploads the generated backup for the active account and verifies it with a direct CloudKit
  read-back. If readable same-account CloudKit exists, the app only rewrites it when a
  same-account KVS candidate is fresher.
- For missing/wrong-account population, recreates the backup locally (via
  `FullAccountCloudBackupCreator` / `LiteAccountCloudBackupCreator`) and uploads using
  `CloudBackupService.writeBackup` with `requireAuthRefresh = false`.
- Records successful active-backup reconciliation locally so the app does not run full
  reconciliation on every launch while the flag remains enabled. When the marker exists, the app
  still does a cheap direct CloudKit/KVS check and reconciles again if CloudKit is missing or a
  same-account KVS candidate is fresher.
- Recovery reads compare same-key CloudKit and KVS backups for the same account and return the
  fresher payload. This lets a fresh install recover from a newer KVS backup after rollback even
  when stale CloudKit data exists for the same key.
- Does **not** delete or modify legacy KVS backups (mirroring continues for now).
