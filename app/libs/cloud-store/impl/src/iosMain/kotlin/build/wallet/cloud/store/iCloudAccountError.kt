package build.wallet.cloud.store

/**
 * Describes a failure that can occur when checking and accessing iCloud account information.
 */
@Suppress("ClassName")
data class iCloudAccountError(override val cause: Throwable) : CloudStoreAccountError()
