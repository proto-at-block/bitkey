package build.wallet.cloud.store

/**
 * Describes a failure that can occur when checking and accessing Google account information.
 */
data class GoogleAccountError(override val cause: Throwable) : CloudStoreAccountError()
