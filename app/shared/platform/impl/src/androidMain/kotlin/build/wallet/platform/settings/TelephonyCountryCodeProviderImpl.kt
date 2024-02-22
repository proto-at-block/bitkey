package build.wallet.platform.settings

import android.content.Context.TELEPHONY_SERVICE
import android.telephony.TelephonyManager
import build.wallet.platform.PlatformContext

actual class TelephonyCountryCodeProviderImpl actual constructor(
  platformContext: PlatformContext,
) : TelephonyCountryCodeProvider {
  private val countryCode by lazy {
    val telephonyManager =
      platformContext.appContext.getSystemService(
        TELEPHONY_SERVICE
      ) as TelephonyManager
    telephonyManager.simCountryIso
  }

  override fun countryCode(): String {
    return countryCode
  }
}
