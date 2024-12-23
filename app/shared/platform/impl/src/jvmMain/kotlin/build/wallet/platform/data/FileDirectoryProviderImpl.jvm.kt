package build.wallet.platform.data

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class FileDirectoryProviderImpl(
  private val appDirOverride: String?,
) : FileDirectoryProvider {
  override fun appDir(): String =
    appDirOverride ?: (System.getProperty("user.dir") + "/_build/bitkey/appdata")
}
