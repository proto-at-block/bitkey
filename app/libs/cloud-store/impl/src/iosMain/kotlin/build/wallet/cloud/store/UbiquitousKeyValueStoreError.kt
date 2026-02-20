package build.wallet.cloud.store

/**
 * Describes a failure that can occur when reading and writing from ubiquitous key-value store.
 */
data class UbiquitousKeyValueStoreError(override val message: String) : CloudError()
