package build.wallet.debug.cloud

actual interface CloudBackupStoreType

object UbiquitousKvs : CloudBackupStoreType

object CloudKit : CloudBackupStoreType

actual val CloudBackupStoreType.name: String
  get() =
    when (this) {
      UbiquitousKvs -> "Ubiquitous KVS"
      CloudKit -> "CloudKit"
      else -> error("Unsupported CloudBackupStoreType on iOS: $this")
    }

actual fun availableCloudBackupStoreTypes(): List<CloudBackupStoreType> =
  listOf(
    UbiquitousKvs,
    CloudKit
  )
