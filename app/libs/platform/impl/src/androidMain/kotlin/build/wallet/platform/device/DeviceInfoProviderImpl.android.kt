package build.wallet.platform.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class DeviceInfoProviderImpl(
  private val context: Context,
) : DeviceInfoProvider {
  override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = Build.MODEL,
      devicePlatform = DevicePlatform.Android,
      // Reference https://github.com/fluttercommunity/plus_plugins/blob/f5244e368c74d8b6e7bdd0062a4a2250dcabe540/packages/device_info_plus/device_info_plus/android/src/main/kotlin/dev/fluttercommunity/plus/device_info/MethodCallHandlerImpl.kt#L110-L125
      isEmulator =
        Build.MODEL.contains("sdk_gphone") ||
          Build.MODEL.contains("google_sdk") ||
          Build.DEVICE.contains("emu64a"),
      deviceNickname = getDeviceNickname()
    )

  private fun getDeviceNickname(): String? {
    // Settings.Global.DEVICE_NAME is only available on Android 7.1+ (API 25)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N_MR1) {
      return null
    }

    // Get device name from Android settings.
    // Note: The device name may not be set by all users or manufacturers; returns null in those cases.
    return Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)
      ?.takeIf { it.isNotBlank() }
  }
}
