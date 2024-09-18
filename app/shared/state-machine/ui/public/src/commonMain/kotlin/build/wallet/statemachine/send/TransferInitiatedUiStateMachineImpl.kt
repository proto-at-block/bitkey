package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.statemachine.core.BodyModel

class TransferInitiatedUiStateMachineImpl(
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : TransferInitiatedUiStateMachine {
  @Composable
  override fun model(props: TransferInitiatedUiProps): BodyModel {
    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
    val transactionDetails = transactionDetailsCardUiStateMachine.model(
      props = TransactionDetailsCardUiProps(
        transactionDetails = props.transactionDetails,
        fiatCurrency = fiatCurrency,
        exchangeRates = props.exchangeRates
      )
    )
    return TransferInitiatedBodyModel(
      onBack = props.onBack,
      recipientAddress = props.recipientAddress.chunkedAddress(),
      transactionDetails = transactionDetails,
      onDone = props.onDone
    )
  }
}
