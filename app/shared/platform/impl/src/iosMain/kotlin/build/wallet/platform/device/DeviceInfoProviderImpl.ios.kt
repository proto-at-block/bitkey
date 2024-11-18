package build.wallet.platform.device

import kotlinx.cinterop.*
import platform.posix.uname
import platform.posix.utsname

actual class DeviceInfoProviderImpl : DeviceInfoProvider {
  actual override fun getDeviceInfo() =
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
