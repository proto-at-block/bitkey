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

    val spentAmountText =
      (props.spendingLimit.amount - props.remainingAmount).let {
        moneyDisplayFormatter.format(it)
      }
    val remainingAmountText = moneyDisplayFormatter.format(props.remainingAmount)

    val progressPercentage =
      with(props) {
        (spendingLimit.amount - remainingAmount).fractionalUnitValue.floatValue() /
          spendingLimit.amount.fractionalUnitValue.floatValue()
      }

    return SpendingLimitCardModel(
      dailyResetTimezoneText = "Resets at 3:00am $dailyResetTimezone",
      spentAmountText = "$spentAmountText spent",
      remainingAmountText = "$remainingAmountText remaining",
      progressPercentage = progressPercentage
    )
  }
}
