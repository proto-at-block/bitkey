package build.wallet.statemachine.send.fee

import androidx.compose.runtime.*
import build.wallet.bitcoin.balance.BitcoinBalance.Companion.ZeroBalance
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class FeeOptionListUiStateMachineImpl(
  private val feeOptionUiStateMachine: FeeOptionUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val bitcoinWalletService: BitcoinWalletService,
) : FeeOptionListUiStateMachine {
  @Composable
  override fun model(props: FeeOptionListProps): FeeOptionList {
    var selectedPriority by remember { mutableStateOf(props.defaultPriority) }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    val transactionsData = remember { bitcoinWalletService.transactionsData() }
      .collectAsState().value

    val bitcoinBalance = transactionsData?.balance ?: ZeroBalance

    return FeeOptionList(
      options = props.fees.keys.map { priority ->
        feeOptionUiStateMachine.model(
          props = FeeOptionProps(
            bitcoinBalance = bitcoinBalance,
            feeAmount = props.fees[priority]!!.amount,
            transactionAmount = props.transactionBaseAmount,
            selected = selectedPriority == priority,
            estimatedTransactionPriority = priority,
            fiatCurrency = fiatCurrency,
            exchangeRates = props.exchangeRates,
            // when all fees equals and the priority to render is the top one, show this text
            showAllFeesEqualText = props.fees.values.distinct().size == 1 && priority == FASTEST,
            onClick = {
              selectedPriority = priority
              props.onOptionSelected(priority)
            }
          )
        )
      }.toImmutableList()
    )
  }
}
