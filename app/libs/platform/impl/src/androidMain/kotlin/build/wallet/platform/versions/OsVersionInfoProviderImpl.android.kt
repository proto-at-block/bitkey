package build.wallet.platform.versions

import android.os.Build
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class OsVersionInfoProviderImpl : OsVersionInfoProvider {
  override fun getOsVersion(): String {
    return Build.VERSION.SDK_INT.toString()
  }

  override fun getNamedOsVersion(): String {
    return "Android ${getOsVersion()}"
  }
}
