package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Guards users who uninstalled the app while mid-way through the W3 hardware
 * upgrade (server auth keys already rotated to W3, cloud backup still sealed
 * with W1) from accidentally starting Lost App & Cloud recovery when they
 * reinstall and tap their W3 Bitkey. When enabled, the cloud-backup restore
 * flow probes the backup's recovery auth pubkey against f8e and — if rejected —
 * shows a blocking "Use your other Bitkey" modal asking the user to tap their
 * W1 instead. Short-term block; to be removed when Lost App & Cloud recovery
 * supports the mid-upgrade state directly. See W-17080.
 *
 * Defaults to false on all builds.
 */
class W3MidUpgradeRecoveryGuardFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-w3-mid-upgrade-recovery-guard-is-enabled",
    title = "W3 Mid-Upgrade Recovery Guard",
    description = "Blocks users who left the W3 upgrade mid-flow from entering " +
      "Lost App & Cloud recovery; prompts them to tap their W1 Bitkey instead.",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
