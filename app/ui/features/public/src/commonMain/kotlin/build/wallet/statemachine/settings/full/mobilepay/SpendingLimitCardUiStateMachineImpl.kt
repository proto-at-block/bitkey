package build.wallet.statemachine.settings.full.mobilepay

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.time.TimeZoneFormatter

@BitkeyInject(ActivityScope::class)
class SpendingLimitCardUiStateMachineImpl(
  val moneyDisplayFormatter: MoneyDisplayFormatter,
  val timeZoneFormatter: TimeZoneFormatter,
) : SpendingLimitCardUiStateMachine {
  @Composable
  override fun model(props: SpendingLimitCardUiProps): SpendingLimitCardModel {
    val dailyResetTimezone =
      timeZoneFormatter.timeZoneShortName(
        timeZone = props.spendingLimit.timezone
      )

    val spentAmountText = moneyDisplayFormatter.format(props.spentAmount)
    val remainingAmountText = moneyDisplayFormatter.format(props.remainingAmount)

    // Progress is computed in sats: a round-trip through fiat would not preserve spent=0.
    val totalBitcoin = props.spentBitcoinAmount + props.remainingBitcoinAmount
    val progressPercentage =
      if (totalBitcoin.isZero) {
        0f
      } else {
        props.spentBitcoinAmount.fractionalUnitValue.floatValue() /
          totalBitcoin.fractionalUnitValue.floatValue()
      }

    return SpendingLimitCardModel(
      dailyResetTimezoneText = "Resets at 3:00am $dailyResetTimezone",
      spentAmountText = "$spentAmountText spent",
      remainingAmountText = "$remainingAmountText remaining",
      progressPercentage = progressPercentage
    )
  }
}
