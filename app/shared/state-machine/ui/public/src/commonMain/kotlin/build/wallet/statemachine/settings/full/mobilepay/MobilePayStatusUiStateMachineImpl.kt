package build.wallet.statemachine.settings.full.mobilepay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.data.mobilepay.MobilePayData.LoadingMobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayDisabledData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayEnabledData
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow

class MobilePayStatusUiStateMachineImpl(
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val spendingLimitCardUiStateMachine: SpendingLimitCardUiStateMachine,
) : MobilePayStatusUiStateMachine {
  @Composable
  override fun model(props: MobilePayUiProps): BodyModel {
    return when (val mobilePayData = props.accountData.mobilePayData) {
      is LoadingMobilePayData -> LoadingMobilePayModel()
      is MobilePayEnabledData -> {
        LaunchedEffect("refresh-balance") {
          mobilePayData.refreshBalance()
        }

        MobilePayEnabledModel(props, mobilePayData)
      }
      is MobilePayDisabledData -> MobilePayDisabledModel(props, mobilePayData)
    }
  }

  @Composable
  private fun LoadingMobilePayModel() =
    LoadingBodyModel(
      message = "Loading Mobile Pay...",
      id = MobilePayEventTrackerScreenId.MOBILE_PAY_LOADING
    )

  @Composable
  private fun MobilePayEnabledModel(
    props: MobilePayUiProps,
    mobilePayData: MobilePayEnabledData,
  ): MobilePayStatusModel {
    var confirmingCancellation by remember { mutableStateOf(false) }

    return MobilePayStatusModel(
      onBack = props.onBack,
      switchIsChecked = true,
      onSwitchCheckedChange = {
        // The switch was enabled and the customer is trying to disable, show an alert
        // to confirm
        confirmingCancellation = true
      },
      dailyLimitRow =
        ActionRow(
          title = "Daily limit",
          sideText = moneyDisplayFormatter.format(mobilePayData.activeSpendingLimit.amount),
          onClick = {
            props.onSetLimitClick(mobilePayData.activeSpendingLimit)
          }
        ),
      disableAlertModel =
        when {
          confirmingCancellation -> {
            disableMobilePayAlertModel(
              onConfirm = {
                mobilePayData.disableMobilePay()
                confirmingCancellation = false
              },
              onCancel = {
                confirmingCancellation = false
              }
            )
          }

          else -> null
        },
      spendingLimitCardModel = SpendingLimitCardModel(mobilePayData)
    )
  }

  @Composable
  private fun SpendingLimitCardModel(
    mobilePayData: MobilePayEnabledData,
  ): SpendingLimitCardModel? {
    return mobilePayData.remainingFiatSpendingAmount?.let {
      spendingLimitCardUiStateMachine.model(
        props =
          SpendingLimitCardUiProps(
            spendingLimit = mobilePayData.activeSpendingLimit,
            remainingAmount = it
          )
      )
    }
  }

  @Composable
  private fun MobilePayDisabledModel(
    props: MobilePayUiProps,
    mobilePayData: MobilePayDisabledData,
  ) = MobilePayStatusModel(
    onBack = props.onBack,
    switchIsChecked = false,
    onSwitchCheckedChange = {
      props.onSetLimitClick(
        // Only pass the most recent limit if it matches the current currency
        mobilePayData.mostRecentSpendingLimit?.takeIf { it.amount.currency == props.fiatCurrency }
      )
    },
    dailyLimitRow = null,
    disableAlertModel = null,
    spendingLimitCardModel = null
  )
}
