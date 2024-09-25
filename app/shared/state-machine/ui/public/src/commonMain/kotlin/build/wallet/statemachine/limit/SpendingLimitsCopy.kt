package build.wallet.statemachine.limit

import build.wallet.statemachine.core.Icon

sealed class SpendingLimitsCopy(
  val title: String,
  val subline: String,
  val setDailyLimitCta: String,
  val loadingStatus: String,
  val transferScreenUnavailableWarning: String,
  val gettingStartedCardPair: Pair<String, Icon>,
  val settingsPair: Pair<Icon, String>,
  val onboardingModal: OnboardingModalCopy,
  val disableAlert: DisableAlertCopy,
) {
  // After changing the application primary currency, we ask the customer to re-enable it with their
  // new currency.
  abstract fun reenableSpendingLimitBottomSheetCopy(
    oldCurrencyString: String,
    newCurrencyString: String,
  ): ReenableSpendingLimitBottomSheetCopy

  object MobilePay : SpendingLimitsCopy(
    title = "Mobile Pay",
    subline = "Leave your device at home, and make small spends with just the key on your phone.",
    setDailyLimitCta = "Set limit",
    loadingStatus = "Loading Mobile Pay...",
    transferScreenUnavailableWarning = "Mobile Pay Unavailable",
    gettingStartedCardPair = Pair("Turn on Mobile Pay", Icon.SmallIconPhone),
    settingsPair = Pair(Icon.SmallIconMobileLimit, "Mobile Pay"),
    onboardingModal = OnboardingModalCopy(
      headline = "Mobile pay",
      subline = "Leave your device at home, and make small spends with just the key on your phone.",
      primaryButtonString = "Enable Mobile Pay"
    ),
    disableAlert = DisableAlertCopy(
      title = "Disable mobile pay?",
      subline = "Turning it back on will require your Bitkey device"
    )
  ) {
    override fun reenableSpendingLimitBottomSheetCopy(
      oldCurrencyString: String,
      newCurrencyString: String,
    ) = ReenableSpendingLimitBottomSheetCopy(
      title = "Re-enable Mobile Pay",
      subline = "We noticed that you changed your currency from $oldCurrencyString to $newCurrencyString. " +
        "Please make sure your Mobile Pay amount is correct.",
      primaryActionString = "Enable Mobile Pay"
    )
  }

  object SpendSettings : SpendingLimitsCopy(
    title = "Transfer without hardware",
    subline = "When on, you can spend up to a set daily limit without your Bitkey device.",
    setDailyLimitCta = "Confirm daily limit",
    loadingStatus = "Loading transfer settings...",
    transferScreenUnavailableWarning = "Transfer without hardware unavailable",
    gettingStartedCardPair = Pair("Customize transfer settings", Icon.SmallIconMobileLimit),
    settingsPair = Pair(Icon.SmallIconMobileLimit, "Transfer settings"),
    onboardingModal = OnboardingModalCopy(
      headline = "Transfer without hardware",
      subline = "Spend up to a set daily limit without your Bitkey device.",
      primaryButtonString = "Got it"
    ),
    disableAlert = DisableAlertCopy(
      title = "Always require hardware?",
      subline = "You will be required to confirm all transfers using your Bitkey device."
    )
  ) {
    override fun reenableSpendingLimitBottomSheetCopy(
      oldCurrencyString: String,
      newCurrencyString: String,
    ) = ReenableSpendingLimitBottomSheetCopy(
      title = "Update daily limit",
      subline = "Your currency changed from $oldCurrencyString to $newCurrencyString. " +
        "It's a good idea to update your daily limit in the new currency.",
      primaryActionString = "Update daily limit"
    )
  }

  companion object {
    fun get(isRevampOn: Boolean): SpendingLimitsCopy {
      return when (isRevampOn) {
        true -> SpendSettings
        false -> MobilePay
      }
    }
  }
}

data class OnboardingModalCopy(
  val headline: String,
  val subline: String,
  val primaryButtonString: String,
)

data class DisableAlertCopy(
  val title: String,
  val subline: String,
)

data class ReenableSpendingLimitBottomSheetCopy(
  val title: String,
  val subline: String,
  val primaryActionString: String,
)
