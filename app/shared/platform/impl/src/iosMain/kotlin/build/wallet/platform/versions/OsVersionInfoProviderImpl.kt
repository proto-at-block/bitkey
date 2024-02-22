package build.wallet.platform.versions

import platform.UIKit.UIDevice

actual class OsVersionInfoProviderImpl actual constructor() : OsVersionInfoProvider {
  override fun getOsVersion(): String {
    return UIDevice.currentDevice.systemVersion
  }

  override fun getNamedOsVersion(): String {
    return "iOS ${getOsVersion()}"
  }
}
