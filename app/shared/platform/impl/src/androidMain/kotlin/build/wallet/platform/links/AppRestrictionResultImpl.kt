package build.wallet.platform.links

import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.Failed
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.None
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.Success

actual fun appRestrictionResult(
  appRestrictions: AppRestrictions,
  packageInfo: PackageInfo,
): AppRestrictionResult {
  // If the package names do not match then the [AppRestriction] does not apply
  // to the [PackageInfo] so we return none.
  if (appRestrictions.packageName != packageInfo.packageName) {
    return None
  }
  // If the minimum version according to the [AppRestriction] is met then we return Success
  // Failed otherwise
  return if (packageInfo.longVersionCode >= appRestrictions.minVersion) {
    Success
  } else {
    Failed(appRestrictions)
  }
}
