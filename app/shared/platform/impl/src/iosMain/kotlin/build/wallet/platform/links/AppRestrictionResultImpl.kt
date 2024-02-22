package build.wallet.platform.links

import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.None

/**
 * Since you cannot verify the version of another app an IOS,
 * we do not check the [AppRestrictions] for it
 */
actual fun appRestrictionResult(
  appRestrictions: AppRestrictions,
  packageInfo: PackageInfo,
): AppRestrictionResult {
  return None
}
