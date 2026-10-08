package build.wallet.platform.device

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.cinterop.*
import platform.Foundation.NSError
import platform.LocalAuthentication.LAContext
import platform.LocalAuthentication.LAErrorDomain
import platform.LocalAuthentication.LAErrorPasscodeNotSet
import platform.LocalAuthentication.LAPolicyDeviceOwnerAuthentication
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

  @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
  override fun getDeviceLockState(): DeviceLockState =
    memScoped {
      val errorPtr = alloc<ObjCObjectVar<NSError?>>().apply { value = null }
      val canEvaluate =
        LAContext().canEvaluatePolicy(LAPolicyDeviceOwnerAuthentication, error = errorPtr.ptr)
      deviceLockStateFromPolicyEvaluation(
        canEvaluate = canEvaluate,
        errorDomain = errorPtr.value?.domain,
        errorCode = errorPtr.value?.code,
        passcodeNotSetErrorDomain = LAErrorDomain,
        passcodeNotSetErrorCode = LAErrorPasscodeNotSet
      )
    }

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
