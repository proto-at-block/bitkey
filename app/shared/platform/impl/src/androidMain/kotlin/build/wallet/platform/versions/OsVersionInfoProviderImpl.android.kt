package build.wallet.platform.versions

import android.os.Build

actual class OsVersionInfoProviderImpl actual constructor() : OsVersionInfoProvider {
  actual override fun getOsVersion(): String {
    return Build.VERSION.SDK_INT.toString()
  }

  actual override fun getNamedOsVersion(): String {
    return "Android ${getOsVersion()}"
  }
}
