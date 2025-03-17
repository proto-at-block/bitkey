package build.wallet.platform.versions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import platform.UIKit.UIDevice

@BitkeyInject(AppScope::class)
class OsVersionInfoProviderImpl : OsVersionInfoProvider {
  override fun getOsVersion(): String {
    return UIDevice.currentDevice.systemVersion
  }

  override fun getNamedOsVersion(): String {
    return "iOS ${getOsVersion()}"
  }
}
