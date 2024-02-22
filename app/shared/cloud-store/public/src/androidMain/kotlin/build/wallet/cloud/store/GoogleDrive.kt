package build.wallet.cloud.store

object GoogleDrive : CloudStoreServiceProvider {
  override val name: String = "Google"
}

actual fun cloudServiceProvider(): CloudStoreServiceProvider = GoogleDrive
