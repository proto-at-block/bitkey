package build.wallet.statemachine.data.mobilepay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_DISABLED
import build.wallet.analytics.v1.Action.ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.limit.MobilePayDisabler
import build.wallet.limit.MobilePayLimitSetter
import build.wallet.limit.MobilePayLimitSetter.SetMobilePayLimitError
import build.wallet.limit.MobilePayStatus
import build.wallet.limit.MobilePayStatus.MobilePayEnabled
import build.wallet.limit.MobilePayStatusProvider
import build.wallet.limit.SpendingLimit
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.statemachine.data.mobilepay.MobilePayData.LoadingMobilePayData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayDisabledData
import build.wallet.statemachine.data.mobilepay.MobilePayData.MobilePayEnabledData
import build.wallet.statemachine.data.money.convertedOrNull
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.launch

class MobilePayDataStateMachineImpl(
  private val mobilePayStatusProvider: MobilePayStatusProvider,
  private val mobilePayLimitSetter: MobilePayLimitSetter,
  private val mobilePayDisabler: MobilePayDisabler,
  private val eventTracker: EventTracker,
  private val currencyConverter: CurrencyConverter,
) : MobilePayDataStateMachine {
  @Composable
  override fun model(props: MobilePayProps): MobilePayData {
    val mobilePayStatus = remember(props.account) {
      mobilePayStatusProvider.status(props.account)
    }.collectAsState(null).value

    val scope = rememberStableCoroutineScope()

    return when (mobilePayStatus) {
      null -> LoadingMobilePayData
      is MobilePayEnabled -> {
        MobilePayEnabledData(
          activeSpendingLimit = mobilePayStatus.activeSpendingLimit,
          balance = mobilePayStatus.balance,
          disableMobilePay = {
            scope.launch {
              mobilePayDisabler
                .disable(account = props.account)
                .onSuccess {
                  eventTracker.track(ACTION_APP_MOBILE_TRANSACTIONS_DISABLED)
                }
            }
          },
          remainingFiatSpendingAmount =
            getRemainingFiatSpendingAmount(
              mobilePayStatus,
              props.fiatCurrency
            ),
          changeSpendingLimit = { newSpendingLimit, _, hwFactorProofOfPossession, onResult ->
            scope.launch {
              setSpendingLimit(props, newSpendingLimit, hwFactorProofOfPossession)
                .apply(onResult)
            }
          },
          refreshBalance = {
            scope.launch {
              mobilePayStatusProvider.refreshStatus()
            }
          }
        )
      }

      is MobilePayStatus.MobilePayDisabled -> {
        MobilePayDisabledData(
          mostRecentSpendingLimit = mobilePayStatus.mostRecentSpendingLimit,
          enableMobilePay = { spendingLimit, _, hwFactorProofOfPossession, onResult ->
            scope.launch {
              setSpendingLimit(props, spendingLimit, hwFactorProofOfPossession)
                .apply(onResult)
            }
          }
        )
      }
    }
  }

  @Composable
  private fun getRemainingFiatSpendingAmount(
    state: MobilePayEnabled,
    fiatCurrency: FiatCurrency,
  ): FiatMoney? {
    return state.balance?.let { balance ->
      convertedOrNull(currencyConverter, balance.spent, fiatCurrency)
        ?.let { spentMoneyInFiat ->
          val balanceLimit = balance.limit.amount
          FiatMoney(fiatCurrency, balanceLimit.value - spentMoneyInFiat.value)
        }
    }
  }

  private suspend fun setSpendingLimit(
    props: MobilePayProps,
    newSpendingLimit: SpendingLimit,
    hwFactorProofOfPossession: HwFactorProofOfPossession,
  ): Result<Unit, SetMobilePayLimitError> =
    mobilePayLimitSetter
      .setLimit(
        account = props.account,
        spendingLimit = newSpendingLimit,
        hwFactorProofOfPossession = hwFactorProofOfPossession
      )
      .onSuccess {
        eventTracker.track(
          ACTION_APP_MOBILE_TRANSACTIONS_ENABLED
        )
      }
}
