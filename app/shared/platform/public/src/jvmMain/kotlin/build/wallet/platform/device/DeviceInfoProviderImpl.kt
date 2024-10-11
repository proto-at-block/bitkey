package build.wallet.platform.device

actual class DeviceInfoProviderImpl : DeviceInfoProvider {
  actual override fun getDeviceInfo() =
    DeviceInfo(
      deviceModel = "jvm",
      devicePlatform = DevicePlatform.Jvm,
      isEmulator = false
    )
}
