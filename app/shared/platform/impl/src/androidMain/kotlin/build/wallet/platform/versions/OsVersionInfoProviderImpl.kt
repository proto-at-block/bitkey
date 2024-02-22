package build.wallet.platform.versions

import android.os.Build

actual class OsVersionInfoProviderImpl actual constructor() : OsVersionInfoProvider {
  override fun getOsVersion(): String {
    return Build.VERSION.SDK_INT.toString()
  }

  override fun getNamedOsVersion(): String {
    return "Android ${getOsVersion()}"
  }
}
