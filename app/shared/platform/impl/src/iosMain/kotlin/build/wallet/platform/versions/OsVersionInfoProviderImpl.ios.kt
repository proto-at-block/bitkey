package build.wallet.platform.versions

import platform.UIKit.UIDevice

actual class OsVersionInfoProviderImpl actual constructor() : OsVersionInfoProvider {
  actual override fun getOsVersion(): String {
    return UIDevice.currentDevice.systemVersion
  }

  actual override fun getNamedOsVersion(): String {
    return "iOS ${getOsVersion()}"
  }
}
