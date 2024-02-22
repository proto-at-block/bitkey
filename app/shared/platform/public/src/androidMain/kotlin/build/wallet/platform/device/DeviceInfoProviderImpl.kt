package build.wallet.platform.device

import android.os.Build

actual class DeviceInfoProviderImpl : DeviceInfoProvider {
  override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = Build.MODEL,
      devicePlatform = DevicePlatform.Android,
      // Reference https://github.com/fluttercommunity/plus_plugins/blob/f5244e368c74d8b6e7bdd0062a4a2250dcabe540/packages/device_info_plus/device_info_plus/android/src/main/kotlin/dev/fluttercommunity/plus/device_info/MethodCallHandlerImpl.kt#L110-L125
      isEmulator =
        Build.MODEL.contains("sdk_gphone") ||
          Build.MODEL.contains("google_sdk") ||
          Build.DEVICE.contains("emu64a")
    )
}
