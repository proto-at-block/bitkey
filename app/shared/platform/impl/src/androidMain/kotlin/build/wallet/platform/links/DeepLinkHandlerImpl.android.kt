package build.wallet.platform.links

import android.content.Intent
import android.net.Uri
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import build.wallet.logging.*
import build.wallet.platform.PlatformContext
import build.wallet.platform.getPackageInformation
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult
import build.wallet.platform.links.OpenDeeplinkResult.AppRestrictionResult.None
import build.wallet.platform.links.OpenDeeplinkResult.Opened

actual class DeepLinkHandlerImpl actual constructor(
  val platformContext: PlatformContext,
) : DeepLinkHandler {
  actual override fun openDeeplink(
    url: String,
    appRestrictions: AppRestrictions?,
  ): OpenDeeplinkResult {
    val appRestrictionResult: AppRestrictionResult =
      if (appRestrictions != null) {
        val packageManger = platformContext.appContext.packageManager
        val packageInfo = packageManger.getPackageInformation(appRestrictions.packageName)
        if (packageInfo != null && VERSION.SDK_INT >= VERSION_CODES.P) {
          appRestrictionResult(
            appRestrictions = appRestrictions,
            packageInfo = AndroidPackageInfo(packageInfo.packageName, packageInfo.longVersionCode)
          )
        } else {
          None
        }
      } else {
        None
      }

    val intent = Intent(Intent.ACTION_VIEW)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val uri =
      Uri.parse(url) ?: run {
        logWarn { "Tried to open an invalid url: $url" }
        return OpenDeeplinkResult.Failed
      }
    intent.setData(uri)
    platformContext.appContext.startActivity(intent)
    return Opened(appRestrictionResult)
  }
}

internal class AndroidPackageInfo(
  override val packageName: String,
  override val longVersionCode: Long,
) : PackageInfo
