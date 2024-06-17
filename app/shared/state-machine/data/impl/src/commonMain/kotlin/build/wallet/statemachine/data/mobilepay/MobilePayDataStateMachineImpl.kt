package build.wallet.statemachine.data.mobilepay

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.limit.MobilePayService
import build.wallet.limit.MobilePayStatus
import build.wallet.limit.MobilePayStatus.MobilePayEnabled
import build.wallet.money.FiatMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.statemachine.data.mobilepay.MobilePayData.*
import build.wallet.statemachine.data.money.convertedOrNull
import kotlinx.coroutines.launch

class MobilePayDataStateMachineImpl(
  private val mobilePayService: MobilePayService,
  private val currencyConverter: CurrencyConverter,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : MobilePayDataStateMachine {
  @Composable
  override fun model(props: MobilePayProps): MobilePayData {
    val mobilePayStatus = remember(props.account) {
      mobilePayService.status(props.account)
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
              mobilePayService.disable(account = props.account)
            }
          },
          remainingFiatSpendingAmount = getRemainingFiatSpendingAmount(mobilePayStatus),
          changeSpendingLimit = { newSpendingLimit, _, hwFactorProofOfPossession, onResult ->
            scope.launch {
              mobilePayService.setLimit(props.account, newSpendingLimit, hwFactorProofOfPossession)
                .apply(onResult)
            }
          },
          refreshBalance = {
            scope.launch {
              mobilePayService.refreshStatus()
            }
          }
        )
      }

      is MobilePayStatus.MobilePayDisabled -> {
        MobilePayDisabledData(
          mostRecentSpendingLimit = mobilePayStatus.mostRecentSpendingLimit,
          enableMobilePay = { spendingLimit, _, hwFactorProofOfPossession, onResult ->
            scope.launch {
              mobilePayService.setLimit(props.account, spendingLimit, hwFactorProofOfPossession)
                .apply(onResult)
            }
          }
        )
      }
    }
  }

  @Composable
  private fun getRemainingFiatSpendingAmount(state: MobilePayEnabled): FiatMoney? {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    return state.balance?.let { balance ->
      convertedOrNull(currencyConverter, balance.spent, fiatCurrency)
        ?.let { spentMoneyInFiat ->
          val balanceLimit = balance.limit.amount
          FiatMoney(fiatCurrency, balanceLimit.value - spentMoneyInFiat.value)
        }
    }
  }
}
