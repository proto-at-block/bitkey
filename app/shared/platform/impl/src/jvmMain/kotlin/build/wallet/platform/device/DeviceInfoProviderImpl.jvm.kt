package build.wallet.platform.device

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class DeviceInfoProviderImpl : DeviceInfoProvider {
  override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = "jvm",
      devicePlatform = DevicePlatform.Jvm,
      isEmulator = false
    )
}
