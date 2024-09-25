package build.wallet.statemachine.settings.full.mobilepay

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.id.MobilePayEventTrackerScreenId
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.feature.flags.MobilePayRevampFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.limit.MobilePayData.MobilePayDisabledData
import build.wallet.limit.MobilePayData.MobilePayEnabledData
import build.wallet.limit.MobilePayService
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.formatter.MoneyDisplayFormatter
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.limit.SpendingLimitsCopy
import build.wallet.ui.model.switch.SwitchCardModel.ActionRow
import kotlinx.coroutines.launch

class MobilePayStatusUiStateMachineImpl(
  private val moneyDisplayFormatter: MoneyDisplayFormatter,
  private val spendingLimitCardUiStateMachine: SpendingLimitCardUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val mobilePayService: MobilePayService,
  private val mobilePayRevampFeatureFlag: MobilePayRevampFeatureFlag,
) : MobilePayStatusUiStateMachine {
  @Composable
  override fun model(props: MobilePayUiProps): BodyModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val spendingLimitsCopy = SpendingLimitsCopy.get(mobilePayRevampFeatureFlag.isEnabled())

    val mobilePayData = remember { mobilePayService.mobilePayData }
      .collectAsState()
      .value

    return when (mobilePayData) {
      null -> LoadingMobilePayModel(message = spendingLimitsCopy.loadingStatus)
      is MobilePayEnabledData -> MobilePayEnabledModel(
        props,
        mobilePayData,
        spendingLimitsCopy.disableAlert.title,
        spendingLimitsCopy.disableAlert.subline
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
              onConfirm = {
                scope.launch {
                  mobilePayService.disable(props.accountData.account)
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
      spendingLimitCardModel = SpendingLimitCardModel(mobilePayData),
      spendingLimitCopy = SpendingLimitsCopy.get(isRevampOn = mobilePayRevampFeatureFlag.isEnabled())
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
    spendingLimitCardModel = null,
    spendingLimitCopy = SpendingLimitsCopy.get(isRevampOn = mobilePayRevampFeatureFlag.isEnabled())
  )
}
