package build.wallet.platform.links

import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult

/**
 * Given certain [AppRestrictions] and [PackageInfo], determines if
 * the [PacakgeInfo] met the criteria of the [AppRestrictions]
 */
expect fun appRestrictionResult(
  appRestrictions: AppRestrictions,
  packageInfo: PackageInfo,
): AppRestrictionResult
