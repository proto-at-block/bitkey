package build.wallet.feature.flags

import build.wallet.feature.FeatureFlag
import build.wallet.feature.FeatureFlagDao
import build.wallet.feature.FeatureFlagValue.BooleanFlag

/**
 * Flag determining whether sweep generation reconciles local keysets against f8e.
 *
 * When enabled, sweep generation fetches the server keyset list even when local keysets are
 * authoritative, and backfills any legacy keysets the local keybox is missing so funds on them can
 * be swept. Sweep generation runs on every foreground and every few minutes, so this is gated to
 * limit the added f8e request volume to accounts we are actively investigating.
 *
 * Deliberately separate from [KeysetRepairFeatureFlag]: repair availability and sweep
 * reconciliation are independent decisions, and we need to be able to disable the repair prompt for
 * an account without also disabling its funds recovery path.
 *
 * Defaults to false on all builds.
 */
class SweepKeysetReconciliationFeatureFlag(
  featureFlagDao: FeatureFlagDao,
) : FeatureFlag<BooleanFlag>(
    identifier = "mobile-sweep-keyset-reconciliation-enabled",
    title = "Sweep Keyset Reconciliation",
    description = "Reconciles local keysets against f8e during sweep generation and backfills " +
      "missing legacy keysets",
    defaultFlagValue = BooleanFlag(false),
    featureFlagDao = featureFlagDao,
    type = BooleanFlag::class
  )
