package build.wallet.platform.web

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.browser.customtabs.CustomTabsIntent
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.platform.PlatformContext
import build.wallet.platform.getPackageInformation

private const val CHROME_PACKAGE = "com.android.chrome"

class InAppBrowserNavigatorImpl(
  private val activity: Activity,
  private val platformContext: PlatformContext,
) : InAppBrowserNavigator {
  private var onCloseBrowser: (() -> Unit)? = null

  /**
   * Will open an in-app chrome browser experience if chrome is available and will kick out to
   * an external default browser otherwise
   */
  override fun open(
    url: String,
    onClose: () -> Unit,
  ) {
    this.onCloseBrowser = onClose
    val packageManager = platformContext.appContext.packageManager
    if (isChromeAvailable(packageManager)) {
      val builder = CustomTabsIntent.Builder()
      val customTabsIntent = builder.build()
      customTabsIntent.intent.setPackage(CHROME_PACKAGE)
      customTabsIntent.intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
      customTabsIntent.launchUrl(activity, Uri.parse(url))
    } else {
      val intent = Intent(Intent.ACTION_VIEW)
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      intent.setData(Uri.parse(url))
      try {
        platformContext.appContext.startActivity(intent)
      } catch (_: ActivityNotFoundException) {
        log(LogLevel.Warn) { "No browsers available" }
        onClose()
      }
    }
  }

  override fun onClose() {
    onCloseBrowser?.invoke()
    onCloseBrowser = null
  }

  override fun close() {
    // Android doesn't provide a way to close custom tabs, but they "close" automatically
    // when a new activity is started as that brings the app to the foreground
    onClose()
  }

  private fun isChromeAvailable(packageManager: PackageManager): Boolean {
    if (packageManager.getPackageInformation(CHROME_PACKAGE) == null) {
      return false
    }

    val chromeInfo =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getApplicationInfo(
          CHROME_PACKAGE,
          PackageManager.ApplicationInfoFlags.of(0)
        )
      } else {
        packageManager.getApplicationInfo(CHROME_PACKAGE, PackageManager.GET_META_DATA)
      }
    return chromeInfo.enabled
  }
}
