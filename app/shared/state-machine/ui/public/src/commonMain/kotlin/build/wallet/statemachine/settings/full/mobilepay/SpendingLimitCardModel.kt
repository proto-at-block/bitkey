package build.wallet.statemachine.settings.full.mobilepay

data class SpendingLimitCardModel(
  val titleText: String = "Today’s limit",
  val dailyResetTimezoneText: String,
  val spentAmountText: String,
  val remainingAmountText: String,
  val progressPercentage: Float,
)
