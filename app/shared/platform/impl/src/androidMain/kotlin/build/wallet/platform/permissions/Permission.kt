package build.wallet.platform.permissions

import android.Manifest.permission
import android.os.Build.VERSION
import android.os.Build.VERSION_CODES
import build.wallet.platform.permissions.Permission.Camera
import build.wallet.platform.permissions.Permission.HapticsVibrator
import build.wallet.platform.permissions.Permission.PushNotifications

/**
 * Converts a [Permission] into an android platform permission
 *
 * @return A nullable string that is the value of the android platform permission. When null,
 * there is no corresponding permission and can be assumed granted
 */
fun Permission.manifestPermission(): String? {
  return when (this) {
    Camera -> permission.CAMERA
    HapticsVibrator -> permission.VIBRATE
    PushNotifications ->
      if (VERSION.SDK_INT >= VERSION_CODES.TIRAMISU) {
        permission.POST_NOTIFICATIONS
      } else {
        null
      }
  }
}
