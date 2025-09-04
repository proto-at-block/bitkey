This module contains domain specific components for creating and recovering Keybox from customer's Cloud Storage.

Cloud Storage access is abstracted away and managed by `:libs:cloud-store` components.

## Cloud Backup Versioning

### Core Principles

1. **Immutable Versions**: Never modify existing backup data classes (`CloudBackupV2`, etc.). Each version is a
   complete, immutable snapshot.

2. **Forward Compatibility**: New app versions must support reading all previous backup versions.

3. **Migration is not (currently) automatic**: Consider whether existing backups must be updated to the newest version, and if so evaluate automation such as via `CloudBackupHealthRepository`

### Creating a New Backup Version

When schema changes are needed:

1. Create a new data class (e.g., `CloudBackupV3`) implementing `CloudBackup`
2. Update `CloudBackupRepository`, `CloudBackupDao` to support daisy chain decoding
3. Create a version-specific restorer (e.g., `CloudBackupV3Restorer`)
4. Update the very many exhaustive `when` statements throughout the app
5. Update cloud backup creators e.g. `FullAccountCloudBackupCreator` to create the new format
6. Consider whether automatic migration to the newest version is needed

### Reading Backups (Daisy Chain Pattern)

Json strings are decoded into CloudBackups in multiple places. See `CloudBackupRepository` and `CloudBackupDao`. 

Implementations should daisy-chain decode, starting with the most recent version.

```kotlin
return Json.decodeFromStringResult<CloudBackupV2>(backupEncoded)
return Json.decodeFromStringResult<CloudBackupV3>(backupEncoded)
   .orElse { Json.decodeFromStringResult<CloudBackupV2>(backupEncoded) }
   .mapError { ... }
```

### Testing Requirements
- Test reading each backup version
- Test migration paths between versions
- Test restoration from each version. See `CloudBackupV2RestorerImplTests` for an examples. 
