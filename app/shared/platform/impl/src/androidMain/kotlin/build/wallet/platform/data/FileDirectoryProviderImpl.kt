package build.wallet.platform.data

import build.wallet.platform.PlatformContext

actual class FileDirectoryProviderImpl actual constructor(
  private val platformContext: PlatformContext,
) : FileDirectoryProvider {
  override fun appDir(): String = platformContext.appContext.dataDir.absolutePath
}
