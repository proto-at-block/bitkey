package build.wallet.cloud.store

@Suppress("ClassName")
object iCloudDrive : CloudStoreServiceProvider {
  override val name: String = "iCloud"
}

actual fun cloudServiceProvider(): CloudStoreServiceProvider = iCloudDrive
