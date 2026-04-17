package build.wallet.debug.cloud

actual interface CloudBackupStoreType

object CloudStore : CloudBackupStoreType

actual val CloudBackupStoreType.name: String
  get() =
    when (this) {
      CloudStore -> "Cloud Store"
      else -> error("Unsupported CloudBackupStoreType on JVM: $this")
    }

actual fun availableCloudBackupStoreTypes(): List<CloudBackupStoreType> = listOf(CloudStore)
