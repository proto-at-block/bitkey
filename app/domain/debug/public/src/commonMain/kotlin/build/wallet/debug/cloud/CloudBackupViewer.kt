package build.wallet.debug.cloud

import com.github.michaelbull.result.Result

/**
 * Debug-only service for listing and deleting cloud backup entries across backup stores.
 */
interface CloudBackupViewer {
  /**
   * Loads current backup entries grouped by store type.
   */
  suspend fun load(): Result<CloudBackupViewerData, CloudBackupViewerLoadError>

  /**
   * Deletes a specific backup key from a specific store.
   */
  suspend fun deleteEntry(
    storeType: CloudBackupStoreType,
    key: String,
  ): Result<Unit, Error>
}

sealed interface CloudBackupViewerData {
  /**
   * No cloud account is currently available.
   */
  data object NoCloudAccount : CloudBackupViewerData

  /**
   * A fully loaded view of all stores and backup entries.
   *
   * @property iosCloudKitBackupEnabled iOS-only CloudKit flag value. Null on non-iOS platforms.
   */
  data class Loaded(
    val iosCloudKitBackupEnabled: Boolean?,
    val stores: List<CloudBackupStoreData>,
  ) : CloudBackupViewerData
}

data class CloudBackupViewerLoadError(
  override val message: String,
  override val cause: Throwable? = null,
) : Error()

data class CloudBackupStoreData(
  val storeType: CloudBackupStoreType,
  val entries: List<CloudBackupEntry>,
  val errorMessage: String?,
)

data class CloudBackupEntry(
  val key: String,
  val value: String,
)
