package build.wallet.platform.device

interface DeviceInfoProvider {
  fun getDeviceInfo(): DeviceInfo

  fun getDeviceLockState(): DeviceLockState
}
