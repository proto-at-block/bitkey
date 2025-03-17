package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel

@BitkeyInject(ActivityScope::class)
class TransferInitiatedUiStateMachineImpl(
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
) : TransferInitiatedUiStateMachine {
  @Composable
  override fun model(props: TransferInitiatedUiProps): BodyModel {
    val transactionDetails = transactionDetailsCardUiStateMachine.model(
      props = TransactionDetailsCardUiProps(
        transactionDetails = props.transactionDetails,
        exchangeRates = props.exchangeRates,
        variant = TransferConfirmationScreenVariant.Regular
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
