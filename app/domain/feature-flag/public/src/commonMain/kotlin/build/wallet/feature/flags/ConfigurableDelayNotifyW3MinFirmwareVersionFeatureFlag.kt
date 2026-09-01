package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue

/**
 * Minimum W3 firmware version required for the configurable Delay & Notify period.
 *
 * W3 firmware must support the SetDelayNotifyPeriod privileged action proof; on older
 * firmware the hardware confirmation fails and the customer gets stuck in a retry loop.
 * When the paired hardware is W3 and its firmware version is below this value (or this
 * value is empty), the Delay & Notify period action is hidden from Security Hub.
 *
 * W1 hardware is not gated by this flag.
 */
class ConfigurableDelayNotifyW3MinFirmwareVersionFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<FeatureFlagValue.StringFlag>(
    identifier = "mobile-configurable-delay-notify-w3-min-firmware-version",
    title = "Configurable Delay & Notify W3 Min Firmware Version",
    description = "Minimum W3 firmware version required to configure the Delay & Notify period. " +
      "Empty means the feature is unavailable on W3.",
    defaultFlagValue = FeatureFlagValue.StringFlag(""),
    featureFlagDao = featureFlagDao,
    type = FeatureFlagValue.StringFlag::class
  )
