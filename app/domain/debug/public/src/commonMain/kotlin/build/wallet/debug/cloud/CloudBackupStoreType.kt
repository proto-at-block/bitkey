package build.wallet.debug.cloud

/**
 * Platform-defined cloud backup backend type (for example Google Drive, iCloud KVS, CloudKit).
 *
 * Unlike [build.wallet.cloud.store.CloudStoreAccount], this does not identify a signed-in account;
 * it only selects which storage backend implementation to operate on.
 */
expect interface CloudBackupStoreType

/**
 * Human-readable store type name for debug menu rows.
 */
expect val CloudBackupStoreType.name: String

/**
 * Backup store types available on the current platform.
 */
expect fun availableCloudBackupStoreTypes(): List<CloudBackupStoreType>
