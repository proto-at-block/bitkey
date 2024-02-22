package build.wallet.platform.data

import build.wallet.platform.PlatformContext

actual class FileDirectoryProviderImpl actual constructor(
  val platformContext: PlatformContext,
) : FileDirectoryProvider {
  override fun appDir(): String =
    platformContext.appDirOverride ?: (System.getProperty("user.dir") + "/_build/bitkey/appdata")
}
