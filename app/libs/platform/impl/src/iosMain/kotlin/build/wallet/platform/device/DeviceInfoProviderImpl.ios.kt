package build.wallet.platform.device

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.cinterop.*
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname

@BitkeyInject(AppScope::class)
class DeviceInfoProviderImpl : DeviceInfoProvider {
  override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = getDeviceModel(),
      devicePlatform = DevicePlatform.IOS,
      isEmulator = false,
      deviceNickname = getDeviceNickname()
    )

  @OptIn(ExperimentalForeignApi::class)
  private fun getDeviceModel(): String {
    memScoped {
      val systemInfo = alloc<utsname>()
      uname(systemInfo.ptr)
      return systemInfo.machine.toKString()
    }
  }

  private fun getDeviceNickname(): String? {
    // UIDevice.currentDevice.name returns user-set device name like "John's iPhone"
    val name = UIDevice.currentDevice.name
    return name.takeIf { it.isNotBlank() }
  }
}
