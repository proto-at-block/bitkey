package build.wallet.feature.flags

import build.wallet.feature.*
import build.wallet.platform.device.DeviceInfoProvider
import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.system.exitProcess

class ComposeUiFeatureFlag(
  featureFlagDao: FeatureFlagDao,
  private val deviceInfoProvider: DeviceInfoProvider,
) : FeatureFlag<FeatureFlagValue.BooleanFlag>(
    identifier = "compose-ui-enabled",
    title = "Compose UI for iOS Enabled",
    description = "WARNING: This will close the app!\nToggle full Compose UI on iOS and closes Bitkey, you must manually relaunch the app.",
    defaultFlagValue = FeatureFlagValue.BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.BooleanFlag::class
  ) {
  fun isEnabled(): Boolean {
    return flagValue().value.isEnabled()
  }

  override fun onFlagChanged(newValue: FeatureFlagValue.BooleanFlag) {
    super.onFlagChanged(newValue)
    val devicePlatform = deviceInfoProvider.getDeviceInfo().devicePlatform
    if (devicePlatform == DevicePlatform.IOS) {
      // The current session will be unaffected so we terminate
      // the App since a new instance must be launched.
      exitProcess(0)
    }
  }
}
