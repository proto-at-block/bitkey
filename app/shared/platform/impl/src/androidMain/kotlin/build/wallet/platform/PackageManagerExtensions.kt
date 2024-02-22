package build.wallet.platform

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.os.Build
import build.wallet.logging.LogLevel.Warn
import build.wallet.logging.log

internal fun PackageManager.getPackageInformation(
  packageName: String,
  flags: Int = 0,
): PackageInfo? {
  return try {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
    } else {
      @Suppress("DEPRECATION")
      getPackageInfo(packageName, flags)
    }
  } catch (e: NameNotFoundException) {
    log(Warn, throwable = e) { "$packageName not installed" }
    null
  }
}
