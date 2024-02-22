package build.wallet.platform.device

data class DeviceInfo(
  /**
   * A string representation of the model of the current device running the app,
   * i.e. 'iPhone15,2' for an iPhone 14 Pro.
   */
  val deviceModel: String,
  /**
   * The platform of the device, either iOS, Android, or JVM.
   */
  val devicePlatform: DevicePlatform,
  /**
   * Best effort detection of whether the device is running inside an emulator.
   */
  val isEmulator: Boolean,
) {
  /**
   * Whether the given device model is known to have NFC issues when other signals are enabled,
   * which turning airplane mode on alleviates.
   */
  fun isAirplaneModeRecommendedForDevice(): Boolean {
    // Currently the list of problematic phones are all iPhone 14 models.
    // The iOS model identifiers can be found here: https://gist.github.com/adamawolf/3048717
    val problematicModels =
      listOf(
        "iPhone14,7",
        "iPhone14,8",
        "iPhone15,2",
        "iPhone15,3"
      )

    return problematicModels.contains(deviceModel)
  }
}
