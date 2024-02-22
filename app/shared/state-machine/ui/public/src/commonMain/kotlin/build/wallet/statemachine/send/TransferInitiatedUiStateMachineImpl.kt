package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.BodyModel

class TransferInitiatedUiStateMachineImpl(
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
) : TransferInitiatedUiStateMachine {
  @Composable
  override fun model(props: TransferInitiatedUiProps): BodyModel {
    val transactionDetails =
      transactionDetailsCardUiStateMachine.model(
        props =
          TransactionDetailsCardUiProps(
            transactionDetail =
              when (props.transferInitiatedVariant) {
                is TransferInitiatedUiProps.Variant.Regular ->
                  TransactionDetailType.Regular(
                    transferBitcoinAmount = props.transferInitiatedVariant.transferBitcoinAmount,
                    feeBitcoinAmount = props.transferInitiatedVariant.feeBitcoinAmount,
                    estimatedTransactionPriority = props.estimatedTransactionPriority
                  )
                is TransferInitiatedUiProps.Variant.SpeedUp ->
                  TransactionDetailType.SpeedUp(
                    transferBitcoinAmount = props.transferInitiatedVariant.transferBitcoinAmount,
                    feeBitcoinAmount = props.transferInitiatedVariant.newFeeAmount,
                    oldFeeBitcoinAmount = props.transferInitiatedVariant.oldFeeAmount
                  )
              },
            fiatCurrency = props.fiatCurrency,
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
