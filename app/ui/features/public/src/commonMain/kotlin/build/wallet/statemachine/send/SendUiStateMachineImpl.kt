package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.platform.permissions.Permission.Camera
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.platform.permissions.PermissionUiProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachine
import build.wallet.statemachine.send.SendUiState.*
import build.wallet.statemachine.send.fee.FeeSelectionUiProps
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetails
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Duration.Companion.minutes

@BitkeyInject(ActivityScope::class)
class SendUiStateMachineImpl(
  private val bitcoinAddressRecipientUiStateMachine: BitcoinAddressRecipientUiStateMachine,
  private val transferAmountEntryUiStateMachine: TransferAmountEntryUiStateMachine,
  private val transferConfirmationUiStateMachine: TransferConfirmationUiStateMachine,
  private val transferInitiatedUiStateMachine: TransferInitiatedUiStateMachine,
  private val bitcoinQrCodeUiScanStateMachine: BitcoinQrCodeUiScanStateMachine,
  private val permissionUiStateMachine: PermissionUiStateMachine,
  private val feeSelectionUiStateMachine: FeeSelectionUiStateMachine,
  private val exchangeRateService: ExchangeRateService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : SendUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: SendUiProps): ScreenModel {
    var uiState: SendUiState by remember {
      mutableStateOf(SelectingRecipientUiState(recipientAddress = null))
    }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    // On initiating the send flow, we grab and lock in the current exchange rates, so we use
    // the same rates over the duration of the flow. This is null when the exchange rates are not
    // available or are out of date due to the customer being offline or unable to communicate with f8e
    val exchangeRates: ImmutableList<ExchangeRate>? by remember {
      mutableStateOf(
        exchangeRateService.mostRecentRatesSinceDurationForCurrency(5.minutes, fiatCurrency)
          ?.toImmutableList()
      )
    }

    // When no exchange rates are available, we default to entering amounts in bitcoin. Otherwise,
    // default to entering amounts in fiat if the amount isn't provided (like through an invoice).
    val defaultAmountEntryAmount by remember {
      mutableStateOf(
        if (exchangeRates.isNullOrEmpty()) {
          BitcoinMoney.zero()
        } else {
          FiatMoney.zero(fiatCurrency)
        }
      )
    }

    return when (val state = uiState) {
      is SelectingRecipientUiState ->
        bitcoinAddressRecipientUiStateMachine.model(
          props =
            BitcoinAddressRecipientUiProps(
              address = state.recipientAddress,
              validInvoiceInClipboard = props.validInvoiceInClipboard,
              onBack = props.onExit,
              onRecipientEntered = { recipientAddress ->
                uiState =
                  EnteringAmountUiState(
                    recipientAddress = recipientAddress,
                    transferMoney = defaultAmountEntryAmount
                  )
              },
              onScanQrCodeClick = {
                uiState =
                  if (permissionUiStateMachine.isImplemented) {
                    RequestingCameraUiState
                  } else {
                    ScanningQrCodeUiState
                  }
              },
              onGoToUtxoConsolidation = props.onGoToUtxoConsolidation
            )
        ).asModalFullScreen()

      is RequestingCameraUiState ->
        permissionUiStateMachine.model(
          PermissionUiProps(
            permission = Camera,
            onExit = {
              uiState = SelectingRecipientUiState(recipientAddress = null)
            },
            onGranted = {
              uiState = ScanningQrCodeUiState
            }
          )
        ).asModalFullScreen()

      is ScanningQrCodeUiState ->
        bitcoinQrCodeUiScanStateMachine.model(
          props =
            BitcoinQrCodeScanUiProps(
              validInvoiceInClipboard = props.validInvoiceInClipboard,
              onEnterAddressClick = {
                uiState = SelectingRecipientUiState(recipientAddress = null)
              },
              onClose = {
                uiState = SelectingRecipientUiState(recipientAddress = null)
              },
              onRecipientScanned = { address ->
                uiState =
                  EnteringAmountUiState(
                    recipientAddress = address,
                    transferMoney = defaultAmountEntryAmount
                  )
              },
              onInvoiceScanned = { invoice ->
                uiState =
                  EnteringAmountUiState(
                    recipientAddress = invoice.address,
                    transferMoney = invoice.amount ?: defaultAmountEntryAmount
                  )
              },
              onGoToUtxoConsolidation = props.onGoToUtxoConsolidation
            )
        )

      is EnteringAmountUiState ->
        transferAmountEntryUiStateMachine.model(
          props = TransferAmountEntryUiProps(
            onBack = {
              uiState = SelectingRecipientUiState(recipientAddress = state.recipientAddress)
            },
            initialAmount = state.transferMoney,
            exchangeRates = exchangeRates
          ) { continueParams ->
            uiState = SelectingTransactionPriorityUiState(
              recipientAddress = state.recipientAddress,
              sendAmount = continueParams.sendAmount
            )
          }
        )

      is ConfirmingTransferUiState ->
        transferConfirmationUiStateMachine.model(
          props = TransferConfirmationUiProps(
            selectedPriority = state.selectedPriority,
            recipientAddress = state.recipientAddress,
            sendAmount = state.sendAmount,
            onExit = props.onExit,
            onBack = {
              uiState = EnteringAmountUiState(
                recipientAddress = state.recipientAddress,
                transferMoney =
                  when (val amount = state.sendAmount) {
                    is ExactAmount -> amount.money
                    is SendAll -> defaultAmountEntryAmount
                  }
              )
            },
            fees = state.fees,
            onTransferFailed = props.onExit,
            exchangeRates = exchangeRates,
            onTransferInitiated = { psbt, priority ->
              uiState = TransferInitiatedUiState(
                recipientAddress = state.recipientAddress,
                transferMoney = BitcoinMoney.sats(psbt.amountSats.toBigInteger()),
                feeBitcoinAmount = psbt.fee,
                estimatedTransactionPriority = priority
              )
            },
            variant = TransferConfirmationScreenVariant.Regular
          )
        )

      is TransferInitiatedUiState ->
        transferInitiatedUiStateMachine.model(
          props = TransferInitiatedUiProps(
            recipientAddress = state.recipientAddress,
            transactionDetails = TransactionDetails.Regular(
              transferAmount = state.transferMoney,
              feeAmount = state.feeBitcoinAmount,
              estimatedTransactionPriority = state.estimatedTransactionPriority
            ),
            exchangeRates = exchangeRates,
            onBack = {
              props.onExit()
            },
            onDone = {
              props.onDone()
            }
          )
        ).asModalFullScreen()

      is SelectingTransactionPriorityUiState ->
        feeSelectionUiStateMachine.model(
          props =
            FeeSelectionUiProps(
              recipientAddress = state.recipientAddress,
              sendAmount = state.sendAmount,
              exchangeRates = exchangeRates,
              onBack = {
                uiState =
                  EnteringAmountUiState(
                    recipientAddress = state.recipientAddress,
                    transferMoney =
                      when (val amount = state.sendAmount) {
                        is ExactAmount -> amount.money
                        is SendAll -> defaultAmountEntryAmount
                      }
                  )
              },
              onContinue = { priority, fees ->
                uiState =
                  ConfirmingTransferUiState(
                    selectedPriority = priority,
                    recipientAddress = state.recipientAddress,
                    sendAmount = state.sendAmount,
                    fees = fees
                  )
              }
            )
        ).asModalFullScreen()
    }
  }
}

private sealed interface SendUiState {
  /**
   * Customer is entering recipient bitcoin address.
   */
  data class SelectingRecipientUiState(
    val recipientAddress: BitcoinAddress?,
  ) : SendUiState

  /**
   * Customer is scanning a qr code to send funds
   */
  data object ScanningQrCodeUiState : SendUiState

  /**
   * Requesting camera to scan QR code
   */
  data object RequestingCameraUiState : SendUiState

  data class SelectingTransactionPriorityUiState(
    val recipientAddress: BitcoinAddress,
    val sendAmount: BitcoinTransactionSendAmount,
  ) : SendUiState

  /**
   * Customer is entering transfer amount.
   */
  data class EnteringAmountUiState(
    val recipientAddress: BitcoinAddress,
    val transferMoney: Money,
  ) : SendUiState

  /**
   * Customer is confirming transfer (signing with hardware if needed).
   */
  data class ConfirmingTransferUiState(
    val selectedPriority: EstimatedTransactionPriority,
    val recipientAddress: BitcoinAddress,
    val sendAmount: BitcoinTransactionSendAmount,
    val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
  ) : SendUiState

  /**
   * Customer successfully initiated transfer.
   */
  data class TransferInitiatedUiState(
    val recipientAddress: BitcoinAddress,
    val transferMoney: BitcoinMoney,
    val feeBitcoinAmount: BitcoinMoney,
    val estimatedTransactionPriority: EstimatedTransactionPriority,
  ) : SendUiState
}
