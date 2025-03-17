package build.wallet.cloud.store

/**
 * Describes a failure that can occur when checking and accessing Google Drive services.
 */
data class GoogleDriveError(
  override val message: String? = null,
  override val cause: Throwable? = null,
  override val rectificationData: Any? = null,
) : CloudError()
