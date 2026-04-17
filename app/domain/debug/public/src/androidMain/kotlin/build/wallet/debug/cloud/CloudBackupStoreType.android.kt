package build.wallet.debug.cloud

actual interface CloudBackupStoreType

object GoogleDrive : CloudBackupStoreType

actual val CloudBackupStoreType.name: String
  get() =
    when (this) {
      GoogleDrive -> "Google Drive"
      else -> error("Unsupported CloudBackupStoreType on Android: $this")
    }

actual fun availableCloudBackupStoreTypes(): List<CloudBackupStoreType> = listOf(GoogleDrive)
