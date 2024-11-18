package build.wallet.platform.device

expect class DeviceInfoProviderImpl() : DeviceInfoProvider {
  override fun getDeviceInfo(): DeviceInfo
}
