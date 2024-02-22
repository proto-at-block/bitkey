package build.wallet.platform.device

interface DeviceInfoProvider {
  fun getDeviceInfo(): DeviceInfo

  companion object {
    val default by lazy { DeviceInfoProviderImpl().getDeviceInfo() }
  }
}
