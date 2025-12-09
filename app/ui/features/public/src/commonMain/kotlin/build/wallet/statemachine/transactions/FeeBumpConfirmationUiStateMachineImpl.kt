package build.wallet.statemachine.transactions

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransaction.TransactionType.*
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority.FASTEST
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.NetworkingError
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.moneyhome.MoneyHomeAppSegment
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.*
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorContext
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiError
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiProps
import build.wallet.statemachine.transactions.fee.FeeEstimationErrorUiStateMachine
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpConfirmationModel
import build.wallet.statemachine.utxo.UtxoConsolidationSpeedUpTransactionSentModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.toImmutableList

@BitkeyInject(ActivityScope::class)
class FeeBumpConfirmationUiStateMachineImpl(
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
  private val exchangeRateService: ExchangeRateService,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val transferInitiatedUiStateMachine: TransferInitiatedUiStateMachine,
  private val bitcoinWalletService: BitcoinWalletService,
  private val feeEstimationErrorUiStateMachine: FeeEstimationErrorUiStateMachine,
) : FeeBumpConfirmationUiStateMachine {
  @Composable
  override fun model(props: FeeBumpConfirmationProps): ScreenModel {
    var uiState: State by remember {
      mutableStateOf(
        State.ConfirmingFeeBump(
          appSignedPsbt = props.psbt,
          feeRate = props.newFeeRate
        )
      )
    }

    val exchangeRates = exchangeRateService.exchangeRates.value.toImmutableList()

    return when (val currentState = uiState) {
      is State.ConfirmingFeeBump -> {
        val transferBitcoinAmount = BitcoinMoney
          .sats(currentState.appSignedPsbt.amountSats.toBigInteger())
        val feeBitcoinAmount = currentState.appSignedPsbt.fee

        val transactionDetails = TransactionDetails.SpeedUp(
          transferAmount = transferBitcoinAmount,
          feeAmount = feeBitcoinAmount,
          oldFeeAmount = props.speedUpTransactionDetails.oldFee.amount
        )

        val transactionDetailsCard = transactionDetailsCardUiStateMachine.model(
          props = TransactionDetailsCardUiProps(
            transactionDetails = transactionDetails,
            exchangeRates = exchangeRates,
            variant = TransferConfirmationScreenVariant.SpeedUp
          )
        )

        val speedUpTransactionDetails =
          transactionDetailsCard.transactionDetailModelType as? TransactionDetailModelType.SpeedUp
            ?: error("Transaction details card state machine created a transaction detail model that wasn't of type SpeedUp")

        return when (props.speedUpTransactionDetails.transactionType) {
          Outgoing -> TransferConfirmationScreenModel(
            onBack = props.onExit,
            variant = TransferConfirmationScreenVariant.SpeedUp,
            recipientAddress = props.speedUpTransactionDetails.recipientAddress,
            transactionDetails = transactionDetailsCard,
            requiresHardware = true, // currently all fee bumps require hardware
            confirmButtonEnabled = true,
            onConfirmClick = {
              uiState = State.SigningWithHardware(currentState.appSignedPsbt)
            },
            onNetworkFeesClick = {},
            onArrivalTimeClick = null,
            requiresHardwareReview = false
          ).asModalFullScreen()
          UtxoConsolidation -> UtxoConsolidationSpeedUpConfirmationModel(
            onBack = props.onExit,
            onCancel = props.onExit,
            recipientAddress = props.speedUpTransactionDetails.recipientAddress.chunkedAddress(),
            transactionSpeedText = transactionDetailsCard.transactionSpeedText,
            originalConsolidationCost = speedUpTransactionDetails.oldFeeAmountText,
            originalConsolidationCostSecondaryText = speedUpTransactionDetails.oldFeeAmountSecondaryText,
            consolidationCostDifference = speedUpTransactionDetails.feeDifferenceText,
            consolidationCostDifferenceSecondaryText = speedUpTransactionDetails.feeDifferenceSecondaryText,
            totalConsolidationCost = speedUpTransactionDetails.totalFeeText,
            totalConsolidationCostSecondaryText = speedUpTransactionDetails.totalFeeSecondaryText,
            onConfirmClick = {
              uiState = State.SigningWithHardware(currentState.appSignedPsbt)
            }
          ).asModalFullScreen()
          Incoming -> error("Can't speed up an incoming transaction")
        }
      }

      is State.SigningWithHardware -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            commands.signTransaction(
              session = session,
              psbt = currentState.appSignedPsbt,
              spendingKeyset = props.account.keybox.activeSpendingKeyset
            )
          },
          onCancel = {
            uiState = State.ConfirmingFeeBump(
              appSignedPsbt = currentState.appSignedPsbt,
              feeRate = props.newFeeRate
            )
          },
          onSuccess = { appAndHwSignedPsbt ->
            uiState = State.BroadcastingTransaction(appAndHwSignedPsbt)
          },
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_TRANSACTION,
          shouldShowLongRunningOperation = true
        )
      )
      is State.BroadcastingTransaction -> {
        LaunchedEffect("broadcasting-txn") {
          bitcoinWalletService
            .broadcast(
              psbt = currentState.appAndHwSignedPsbt,
              estimatedTransactionPriority = FASTEST
            )
            .logFailure { "Error broadcasting fee bump transaction." }
            .onSuccess {
              uiState = State.ViewingFeeBumpConfirmation(currentState.appAndHwSignedPsbt)
            }
            .onFailure { error ->
              uiState =
                State.BroadcastFailed(
                  appAndHwSignedPsbt = currentState.appAndHwSignedPsbt,
                  error = error.toFeeEstimationError(),
                  cause = error
                )
            }
        }

        LoadingBodyModel(
          title = "Initiating transfer...",
          onBack = {
            uiState = State.ConfirmingFeeBump(
              appSignedPsbt = props.psbt,
              feeRate = props.newFeeRate
            )
          },
          id = SendEventTrackerScreenId.SEND_SIGNING_AND_BROADCASTING_LOADING,
          eventTrackerShouldTrack = false
        ).asModalScreen()
      }
      is State.BroadcastFailed -> {
        val retryHandler =
          when (currentState.error) {
            is FeeEstimationErrorUiError.LoadFailed ->
              {
                {
                  uiState = State.BroadcastingTransaction(currentState.appAndHwSignedPsbt)
                }
              }
            else -> null
          }

        feeEstimationErrorUiStateMachine
          .model(
            FeeEstimationErrorUiProps(
              error = currentState.error,
              onBack = props.onExit,
              onRetry = retryHandler,
              errorData = broadcastErrorData(currentState.cause),
              context = FeeEstimationErrorContext.SpeedUp
            )
          ).asModalScreen()
      }
      is State.ViewingFeeBumpConfirmation -> {
        val transactionDetails = TransactionDetails.SpeedUp(
          transferAmount = BitcoinMoney
            .sats(currentState.appAndHwSignedPsbt.amountSats.toBigInteger()),
          oldFeeAmount = props.speedUpTransactionDetails.oldFee.amount,
          feeAmount = currentState.appAndHwSignedPsbt.fee
        )

        when (props.speedUpTransactionDetails.transactionType) {
          Outgoing ->
            transferInitiatedUiStateMachine
              .model(
                props = TransferInitiatedUiProps(
                  recipientAddress = props.speedUpTransactionDetails.recipientAddress,
                  transactionDetails = transactionDetails,
                  exchangeRates = exchangeRates,
                  onBack = props.onExit,
                  onDone = props.onExit
                )
              ).asModalFullScreen()
          UtxoConsolidation -> {
            val transactionDetailsCard = transactionDetailsCardUiStateMachine.model(
              props = TransactionDetailsCardUiProps(
                transactionDetails = transactionDetails,
                exchangeRates = exchangeRateService.exchangeRates.value.toImmutableList(),
                variant = TransferConfirmationScreenVariant.Regular
              )
            )

            val speedUpTransactionDetails =
              transactionDetailsCard.transactionDetailModelType as? TransactionDetailModelType.SpeedUp
                ?: error("Transaction details card state machine created a transaction detail model that wasn't of type SpeedUp")

            UtxoConsolidationSpeedUpTransactionSentModel(
              targetAddress = props.speedUpTransactionDetails.recipientAddress.chunkedAddress(),
              arrivalTime = transactionDetailsCard.transactionSpeedText,
              originalConsolidationCost = speedUpTransactionDetails.oldFeeAmountText,
              originalConsolidationCostSecondaryText = speedUpTransactionDetails.oldFeeAmountSecondaryText,
              consolidationCostDifference = speedUpTransactionDetails.feeDifferenceText,
              consolidationCostDifferenceSecondaryText = speedUpTransactionDetails.feeDifferenceSecondaryText,
              totalConsolidationCost = speedUpTransactionDetails.totalFeeText,
              totalConsolidationCostSecondaryText = speedUpTransactionDetails.totalFeeSecondaryText,
              onBack = props.onExit,
              onDone = props.onExit
            ).asModalFullScreen()
          }
          Incoming -> error("Can't speed up an incoming transaction")
        }
      }
    }
  }
}

sealed interface State {
  data class ConfirmingFeeBump(
    val appSignedPsbt: Psbt,
    val isLoadingRates: Boolean = false,
    val feeRate: FeeRate,
  ) : State

  data class SigningWithHardware(
    val appSignedPsbt: Psbt,
  ) : State

  data class BroadcastingTransaction(
    val appAndHwSignedPsbt: Psbt,
  ) : State

  data class ViewingFeeBumpConfirmation(
    val appAndHwSignedPsbt: Psbt,
  ) : State

  data class BroadcastFailed(
    val appAndHwSignedPsbt: Psbt,
    val error: FeeEstimationErrorUiError,
    val cause: Error,
  ) : State
}

private fun broadcastErrorData(cause: Throwable) =
  ErrorData(
    segment = MoneyHomeAppSegment.Transactions,
    actionDescription = "Broadcasting a fee bump transaction",
    cause = cause
  )

private fun Error.toFeeEstimationError(): FeeEstimationErrorUiError =
  when (this) {
    is BdkError.InsufficientFunds -> FeeEstimationErrorUiError.InsufficientFunds
    is BdkError.FeeRateTooLow, is BdkError.FeeTooLow -> FeeEstimationErrorUiError.FeeRateTooLow
    is BdkError.Esplora,
    is BdkError.Electrum,
    is BdkError.Rpc,
    is NetworkingError,
    -> FeeEstimationErrorUiError.LoadFailed(isConnectivityError = true)
    else -> FeeEstimationErrorUiError.Generic
  }
