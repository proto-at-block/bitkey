package build.wallet.platform.settings

import android.telephony.TelephonyManager
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class TelephonyCountryCodeProviderImpl(
  private val telephonyManager: TelephonyManager,
) : TelephonyCountryCodeProvider {
  private val countryCode by lazy {
    telephonyManager.simCountryIso.uppercase()
  }

  override fun countryCode(): String {
    return countryCode
  }
}
