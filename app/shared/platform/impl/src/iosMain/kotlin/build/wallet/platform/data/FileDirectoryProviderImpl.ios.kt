package build.wallet.platform.data

import build.wallet.platform.PlatformContext
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual class FileDirectoryProviderImpl actual constructor(
  platformContext: PlatformContext,
) : FileDirectoryProvider {
  actual override fun appDir(): String {
    val paths =
      NSSearchPathForDirectoriesInDomains(
        directory = NSApplicationSupportDirectory,
        domainMask = NSUserDomainMask,
        expandTilde = true
      )
    return (paths[0] as String)
  }
}
