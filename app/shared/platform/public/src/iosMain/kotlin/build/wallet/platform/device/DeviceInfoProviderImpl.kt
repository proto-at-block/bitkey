package build.wallet.platform.device

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.posix.uname
import platform.posix.utsname

actual class DeviceInfoProviderImpl : DeviceInfoProvider {
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
