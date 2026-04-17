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
  private val sendAmountEntryUiStateMachine: SendAmountEntryUiStateMachine,
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

    // Snapshot at flow entry for determining default input currency (stable, doesn't change mid-flow)
    val initialRates = remember {
      exchangeRateService.mostRecentRatesSinceDurationForCurrency(6.minutes, fiatCurrency)
        ?.toImmutableList()
    }

    // Locked rates: set when user advances past amount entry, used for rest of flow
    var lockedExchangeRates: ImmutableList<ExchangeRate>? by remember {
      mutableStateOf(initialRates)
    }
    var rateLocked by remember { mutableStateOf(initialRates != null) }

    // Observe live rates reactively so Compose recomposes when rates change,
    // allowing fiat conversion to appear dynamically if rates arrive after flow start
    val liveRates by exchangeRateService.exchangeRates.collectAsState()

    // Effective rates: use locked rates once set, otherwise derive from fresh live rates.
    // rateLocked ensures that even null rates are locked after advancing past amount entry.
    // The freshness check prevents stale persisted rates from a prior session from leaking through.
    // Falls back to previously locked rates if the freshness check fails after unlock-on-back,
    // preventing a fiat transferMoney + null rates crash.
    val exchangeRates: ImmutableList<ExchangeRate>? = if (rateLocked) {
      lockedExchangeRates
    } else {
      liveRates // Read to trigger recomposition
      exchangeRateService.mostRecentRatesSinceDurationForCurrency(6.minutes, fiatCurrency)
        ?.toImmutableList()
        ?: lockedExchangeRates
    }

    // Fallback: if no fresh rates at flow entry, trigger a sync as a last resort
    LaunchedEffect(Unit) {
      if (initialRates == null) {
        exchangeRateService.syncRates()
      }
    }

    // When no exchange rates are available at flow start, default to entering amounts in bitcoin.
    // Otherwise, default to entering amounts in fiat if the amount isn't provided (like through
    // an invoice). Based on initial snapshot so the input currency doesn't flip mid-flow.
    val defaultAmountEntryAmount by remember {
      mutableStateOf(
        if (initialRates.isNullOrEmpty()) {
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
        sendAmountEntryUiStateMachine.model(
          props = SendAmountEntryUiProps(
            recipientAddress = state.recipientAddress,
            onBack = {
              uiState = SelectingRecipientUiState(recipientAddress = state.recipientAddress)
            },
            initialAmount = state.transferMoney,
            exchangeRates = exchangeRates,
            onContinueClick = { sendAmount ->
              // Lock exchange rates for consistency in fee selection and confirmation screens
              lockedExchangeRates = exchangeRates
              rateLocked = true
              uiState = SelectingTransactionPriorityUiState(
                recipientAddress = state.recipientAddress,
                sendAmount = sendAmount
              )
            },
            onContinueWithPreBuiltPsbts = { sendAmount, psbts ->
              lockedExchangeRates = exchangeRates
              rateLocked = true
              uiState = SelectingTransactionPriorityUiState(
                recipientAddress = state.recipientAddress,
                sendAmount = sendAmount,
                preBuiltPsbts = psbts
              )
            }
          )
        )

      is ConfirmingTransferUiState ->
        transferConfirmationUiStateMachine.model(
          props = TransferConfirmationUiProps(
            account = props.account,
            selectedPriority = state.selectedPriority,
            recipientAddress = state.recipientAddress,
            sendAmount = state.sendAmount,
            onExit = props.onExit,
            onBack = {
              // Unlock rates so amount entry can observe live rates again.
              // Keep lockedExchangeRates as fallback if fresh rates aren't available.
              rateLocked = false
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
            preBuiltPsbts = state.preBuiltPsbts,
            onTransferFailed = props.onExit,
            exchangeRates = exchangeRates,
            onTransferInitiated = { psbt, priority ->
              uiState = TransferInitiatedUiState(
                recipientAddress = state.recipientAddress,
                transferMoney = BitcoinMoney.sats(psbt.amountSats.toBigInteger()),
                feeBitcoinAmount = psbt.fee.amount,
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
              preBuiltPsbts = state.preBuiltPsbts,
              onBack = {
                // Unlock rates so amount entry can observe live rates again.
                // Keep lockedExchangeRates as fallback if fresh rates aren't available.
                rateLocked = false
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
                    fees = fees,
                    preBuiltPsbts = state.preBuiltPsbts
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
    val preBuiltPsbts: build.wallet.bitcoin.transactions.PsbtsForSendAmount? = null,
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
    val preBuiltPsbts: build.wallet.bitcoin.transactions.PsbtsForSendAmount? = null,
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
