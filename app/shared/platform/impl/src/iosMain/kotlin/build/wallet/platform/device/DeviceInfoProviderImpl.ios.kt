package build.wallet.platform.device

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.cinterop.*
import platform.posix.uname
import platform.posix.utsname

@BitkeyInject(AppScope::class)
class DeviceInfoProviderImpl : DeviceInfoProvider {
  override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = getDeviceModel(),
      devicePlatform = DevicePlatform.IOS,
      isEmulator = false
    )

  @OptIn(ExperimentalForeignApi::class)
  private fun getDeviceModel(): String {
    memScoped {
      val systemInfo = alloc<utsname>()
      uname(systemInfo.ptr)
      return systemInfo.machine.toKString()
    }
  }
}
