package build.wallet.statemachine.transactions

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.SendEventTrackerScreenId
import build.wallet.bitcoin.blockchain.BitcoinBlockchain
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.*
import build.wallet.logging.logFailure
import build.wallet.money.BitcoinMoney
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.send.*
import build.wallet.statemachine.send.fee.FeeSelectionEventTrackerScreenId
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.toImmutableList

class FeeBumpConfirmationUiStateMachineImpl(
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val transactionDetailsCardUiStateMachine: TransactionDetailsCardUiStateMachine,
  private val exchangeRateService: ExchangeRateService,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val bitcoinBlockchain: BitcoinBlockchain,
  private val outgoingTransactionDetailRepository: OutgoingTransactionDetailRepository,
  private val transferInitiatedUiStateMachine: TransferInitiatedUiStateMachine,
  private val transactionsService: TransactionsService,
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

    return when (val currentState = uiState) {
      is State.ConfirmingFeeBump -> {
        val transferBitcoinAmount = BitcoinMoney
          .sats(currentState.appSignedPsbt.amountSats.toBigInteger())
        val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()
        val feeBitcoinAmount = currentState.appSignedPsbt.fee

        val transactionDetails = TransactionDetails.SpeedUp(
          transferAmount = transferBitcoinAmount,
          feeAmount = feeBitcoinAmount,
          oldFeeAmount = props.speedUpTransactionDetails.oldFee.amount
        )

        val transactionDetailsCard = transactionDetailsCardUiStateMachine.model(
          props = TransactionDetailsCardUiProps(
            transactionDetails = transactionDetails,
            fiatCurrency = fiatCurrency,
            exchangeRates = exchangeRateService.exchangeRates.value.toImmutableList()
          )
        )

        return TransferConfirmationScreenModel(
          onBack = props.onExit,
          onCancel = props.onExit,
          variant = TransferConfirmationUiProps.Variant.SpeedUp(
            txid = props.speedUpTransactionDetails.txid,
            oldFee = props.speedUpTransactionDetails.oldFee,
            newFeeRate = currentState.feeRate
          ),
          recipientAddress = props.speedUpTransactionDetails.recipientAddress.chunkedAddress(),
          transactionDetails = transactionDetailsCard,
          requiresHardware = true, // currently all fee bumps require hardware
          confirmButtonEnabled = true,
          onConfirmClick = {
            uiState = State.SigningWithHardware(currentState.appSignedPsbt)
          },
          onNetworkFeesClick = {},
          onArrivalTimeClick = null,
          errorOverlayModel = null
        )
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
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_TRANSACTION
        )
      )
      is State.BroadcastingTransaction -> {
        LaunchedEffect("broadcasting-txn") {
          bitcoinBlockchain
            .broadcast(psbt = currentState.appAndHwSignedPsbt)
            .onSuccess {
              transactionsService.syncTransactions()
              // When we successfully broadcast the transaction, store the transaction details and
              // exchange rate.
              outgoingTransactionDetailRepository.persistDetails(
                details =
                  OutgoingTransactionDetail(
                    broadcastDetail = it,
                    exchangeRates = null,
                    estimatedConfirmationTime = it.broadcastTime
                      .plus(EstimatedTransactionPriority.FASTEST.toDuration())
                  )
              )
              uiState = State.ViewingFeeBumpConfirmation(currentState.appAndHwSignedPsbt)
            }.logFailure { "Failed to broadcast transaction" }
            .onFailure {
              uiState = State.UnableToBumpFee
            }
        }

        LoadingBodyModel(
          message = "Initiating transfer...",
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
      State.UnableToBumpFee -> ErrorFormBodyModel(
        title = "We couldn’t speed up this transaction",
        subline = "We are looking into this. Please try again later.",
        primaryButton =
          ButtonDataModel(
            text = "Go Back",
            onClick = { props.onExit() }
          ),
        eventTrackerScreenId = FeeSelectionEventTrackerScreenId.FEE_ESTIMATION_INSUFFICIENT_FUNDS_ERROR_SCREEN
      ).asModalScreen()
      is State.ViewingFeeBumpConfirmation ->
        transferInitiatedUiStateMachine
          .model(
            props = TransferInitiatedUiProps(
              recipientAddress = props.speedUpTransactionDetails.recipientAddress,
              transactionDetails = TransactionDetails.SpeedUp(
                transferAmount = BitcoinMoney
                  .sats(currentState.appAndHwSignedPsbt.amountSats.toBigInteger()),
                oldFeeAmount = props.speedUpTransactionDetails.oldFee.amount,
                feeAmount = currentState.appAndHwSignedPsbt.fee
              ),
              exchangeRates = null,
              onBack = {
                props.onExit()
              },
              onDone = {
                props.onExit()
              }
            )
          ).asModalFullScreen()
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

  data object UnableToBumpFee : State
}
