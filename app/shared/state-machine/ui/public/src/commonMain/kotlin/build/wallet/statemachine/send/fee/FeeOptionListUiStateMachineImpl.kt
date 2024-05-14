package build.wallet.statemachine.send.fee

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.statemachine.core.form.FormMainContentModel.FeeOptionList
import kotlinx.collections.immutable.toImmutableList

class FeeOptionListUiStateMachineImpl(
  private val feeOptionUiStateMachine: FeeOptionUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : FeeOptionListUiStateMachine {
  @Composable
  override fun model(props: FeeOptionListProps): FeeOptionList {
    var selectedPriority by remember { mutableStateOf(props.defaultPriority) }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    return FeeOptionList(
      options = props.fees.keys.map { priority ->
        feeOptionUiStateMachine.model(
          props = FeeOptionProps(
            bitcoinBalance = props.accountData.transactionsData.balance,
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
