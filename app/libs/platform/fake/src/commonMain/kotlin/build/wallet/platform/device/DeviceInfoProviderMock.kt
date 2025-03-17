package build.wallet.platform.device

class DeviceInfoProviderMock : DeviceInfoProvider {
  var deviceModelValue = "device-model"
  var devicePlatformValue = DevicePlatform.Jvm

  override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = deviceModelValue,
      devicePlatform = devicePlatformValue,
      isEmulator = false
    )

  fun reset() {
    deviceModelValue = "device-model"
    devicePlatformValue = DevicePlatform.Jvm
  }
}
