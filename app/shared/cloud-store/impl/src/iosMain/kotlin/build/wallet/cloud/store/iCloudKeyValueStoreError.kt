package build.wallet.cloud.store

/**
 * Describes a failure that can occur when reading and writing from iCloud.
 */
@Suppress("ClassName")
data class iCloudKeyValueStoreError(override val message: String) : CloudError()
