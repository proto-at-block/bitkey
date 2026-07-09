package build.wallet.ui.app.qrcode

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import platform.Foundation.NSProcessInfo
import platform.UIKit.UIDevice
import platform.UIKit.UIUserInterfaceIdiomPhone
import platform.posix.uname
import platform.posix.utsname

internal actual val usesDynamicIslandQrScannerPortal: Boolean
  get() {
    if (UIDevice.currentDevice.userInterfaceIdiom != UIUserInterfaceIdiomPhone) {
      return false
    }

    return isKnownDynamicIslandIPhoneModel(currentDeviceModelIdentifier())
  }

private fun currentDeviceModelIdentifier(): String {
  simulatorModelIdentifier()?.let { return it }

  return hardwareModelIdentifier()
}

private fun simulatorModelIdentifier(): String? {
  return NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] as? String
}

@OptIn(ExperimentalForeignApi::class)
private fun hardwareModelIdentifier(): String {
  memScoped {
    val systemInfo = alloc<utsname>()
    uname(systemInfo.ptr)
    return systemInfo.machine.toKString()
  }
}
