package build.wallet.statemachine.partnerships

import build.wallet.platform.links.AppRestrictions

/**
 * The way the Partner Flow should be redirected
 * Upon going into a Partner's transfer flow
 * we will be redirected to their platform.
 */
sealed class PartnerRedirectionMethod {
  /**
   * Partner is redirected via deeplink
   * @param urlString - the url needed to open the deeplink
   * @param appRestrictions - specifies the minimum version of the app and the package name, applicable for Android only
   * @param partnerName - specifies the partner name used for customers
   */
  data class Deeplink(
    val urlString: String,
    val appRestrictions: AppRestrictions?,
    val partnerName: String,
  ) : PartnerRedirectionMethod()

  /**
   * Partner is redirected via a web url
   */
  data class Web(val urlString: String) : PartnerRedirectionMethod()
}
