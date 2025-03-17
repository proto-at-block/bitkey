package build.wallet.statemachine.settings.full.mobilepay

import bitkey.ui.Snapshot
import bitkey.ui.Snapshots

data class SpendingLimitCardModel(
  val titleText: String = "Today’s limit",
  val dailyResetTimezoneText: String,
  val spentAmountText: String,
  val remainingAmountText: String,
  // TODO(W-8034): use Progress type.
  val progressPercentage: Float,
)

@Snapshot
val Snapshots.defaultModel
  get() = SpendingLimitCardModel(
    dailyResetTimezoneText = "Resets at 3:00am PDT",
    spentAmountText = "$50.00 spent",
    remainingAmountText = "$50.00 remaining",
    progressPercentage = .5f
  )
