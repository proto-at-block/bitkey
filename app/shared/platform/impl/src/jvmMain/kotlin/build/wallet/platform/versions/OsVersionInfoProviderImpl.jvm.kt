package build.wallet.platform.versions

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class OsVersionInfoProviderImpl : OsVersionInfoProvider {
  override fun getOsVersion(): String = "N/A"

  override fun getNamedOsVersion(): String = "N/A"
}
