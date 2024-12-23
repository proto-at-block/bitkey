package build.wallet.platform.data

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

@BitkeyInject(AppScope::class)
class FileDirectoryProviderImpl : FileDirectoryProvider {
  override fun appDir(): String {
    val paths =
      NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true
      )
    return (paths[0] as String)
  }
}
