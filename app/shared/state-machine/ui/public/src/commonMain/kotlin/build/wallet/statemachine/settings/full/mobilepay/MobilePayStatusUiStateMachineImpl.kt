package build.wallet.statemachine.settings.full.mobilepay

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.limit.MobilePayData.MobilePayDisabledData
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayService
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class MobilePayStatusUiStateMachineImpl(
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val spendingLimitCardUiStateMachine: SpendingLimitCardUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val mobilePayService: MobilePayService,
) : MobilePayStatusUiStateMachine {
  @Composable
  override fun model(props: MobilePayUiProps): BodyModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val mobilePayData = remember { mobilePayService.mobilePayData }
      .collectAsState()
      .value

    return when (mobilePayData) {
      null -> LoadingMobilePayModel(message = "Loading transfer settings...")
      is MobilePayEnabledData -> MobilePayEnabledModel(
        props = props,
        mobilePayData = mobilePayData,
        disableTitle = "Always require hardware?",
        disableSubline = "You will be required to confirm all transfers using your Bitkey device.",
        disablePrimaryButtonText = "Yes",
        disableCancelText = "Nevermind"
      )
      is MobilePayDisabledData -> MobilePayDisabledModel(props, fiatCurrency, mobilePayData)
    }
  }

  @Composable
  private fun LoadingMobilePayModel(message: String) =
    LoadingBodyModel(
      message = message,
      id = MobilePayEventTrackerScreenId.MOBILE_PAY_LOADING
    )

  @Composable
  private fun MobilePayEnabledModel(
    props: MobilePayUiProps,
    mobilePayData: MobilePayEnabledData,
    disableTitle: String,
    disableSubline: String,
    disablePrimaryButtonText: String,
    disableCancelText: String,
  ): MobilePayStatusModel {
    var confirmingCancellation by remember { mutableStateOf(false) }
    val scope = rememberStableCoroutineScope()

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
              title = disableTitle,
              subline = disableSubline,
              primaryButtonText = disablePrimaryButtonText,
              cancelText = disableCancelText,
              onConfirm = {
                scope.launch {
                  mobilePayService.disable(props.account)
                  confirmingCancellation = false
                }
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
    fiatCurrency: FiatCurrency,
    mobilePayData: MobilePayDisabledData,
  ) = MobilePayStatusModel(
    onBack = props.onBack,
    switchIsChecked = false,
    onSwitchCheckedChange = {
      props.onSetLimitClick(
        // Only pass the most recent limit if it matches the current currency
        mobilePayData.mostRecentSpendingLimit?.takeIf { it.amount.currency == fiatCurrency }
      )
    },
    dailyLimitRow = null,
    disableAlertModel = null,
    spendingLimitCardModel = null
  )
}
