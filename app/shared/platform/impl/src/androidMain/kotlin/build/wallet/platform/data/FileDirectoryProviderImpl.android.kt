package build.wallet.platform.data

import android.app.Application
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class FileDirectoryProviderImpl(
  private val application: Application,
) : FileDirectoryProvider {
  override fun appDir(): String = application.dataDir.absolutePath
}
