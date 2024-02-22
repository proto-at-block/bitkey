package build.wallet.platform.settings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings

class SystemSettingsLauncherImpl(
  private val activity: Activity,
) : SystemSettingsLauncher {
  override fun launchSettings() {
    val intent =
      Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .apply {
          data = Uri.fromParts("package", activity.packageName, null)
        }
    activity.startActivity(intent)
  }
}
