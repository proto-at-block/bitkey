package build.wallet.platform.device

class DeviceInfoProviderMock : DeviceInfoProvider {
  var deviceModelValue = "device-model"
  var devicePlatformValue = DevicePlatform.Jvm
  var deviceNicknameValue: String? = null

  override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = deviceModelValue,
      devicePlatform = devicePlatformValue,
      isEmulator = false,
      deviceNickname = deviceNicknameValue
    )

  fun reset() {
    deviceModelValue = "device-model"
    devicePlatformValue = DevicePlatform.Jvm
    deviceNicknameValue = null
  }
}
