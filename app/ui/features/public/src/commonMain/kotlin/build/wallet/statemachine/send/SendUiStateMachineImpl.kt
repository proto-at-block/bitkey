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
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.platform.permissions.PermissionUiProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachine
import build.wallet.statemachine.send.SendUiState.*
import build.wallet.statemachine.send.fee.FeeSelectionUiProps
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachine
import build.wallet.statemachine.transactions.TransactionDetails
import build.wallet.ui.app.qrcode.performDynamicIslandQrScanSuccessHaptic as performQrScanSuccessHaptic
import build.wallet.ui.app.qrcode.usesDynamicIslandQrScannerPortal
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
  private val usesDynamicIslandQrScannerPortalProvider: () -> Boolean = {
    usesDynamicIslandQrScannerPortal
  },
  private val onDynamicIslandQrScanSuccess: () -> Unit = ::performQrScanSuccessHaptic,
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
    var isDynamicIslandQrScannerClosing by remember { mutableStateOf(false) }

    return when (val state = uiState) {
      is SelectingRecipientUiState ->
        recipientAddressBodyModel(
          recipientAddress = state.recipientAddress,
          showToolbarIcons = true,
          props = props,
          defaultAmountEntryAmount = defaultAmountEntryAmount,
          setDynamicIslandQrScannerClosing = { isDynamicIslandQrScannerClosing = it },
          onStateChange = { uiState = it }
        ).asModalFullScreen()

      is RequestingCameraUiState ->
        permissionUiStateMachine.model(
          PermissionUiProps(
            permission = Camera,
            onExit = {
              uiState = SelectingRecipientUiState(recipientAddress = state.recipientAddress)
            },
            onGranted = {
              isDynamicIslandQrScannerClosing = false
              uiState =
                ScanningQrCodeUiState(
                  recipientAddress = state.recipientAddress,
                  isClosingDynamicIslandQrScanner = false
                )
            }
          )
        ).asModalFullScreen()

      is ScanningQrCodeUiState ->
        scanningRecipientAddressModel(
          state = state,
          props = props,
          defaultAmountEntryAmount = defaultAmountEntryAmount,
          isDynamicIslandQrScannerClosing = { isDynamicIslandQrScannerClosing },
          setDynamicIslandQrScannerClosing = { isDynamicIslandQrScannerClosing = it },
          onStateChange = { uiState = it }
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

  @Composable
  private fun recipientAddressBodyModel(
    recipientAddress: BitcoinAddress?,
    showToolbarIcons: Boolean,
    props: SendUiProps,
    defaultAmountEntryAmount: Money,
    setDynamicIslandQrScannerClosing: (Boolean) -> Unit,
    onStateChange: (SendUiState) -> Unit,
  ): BodyModel =
    bitcoinAddressRecipientUiStateMachine.model(
      props =
        BitcoinAddressRecipientUiProps(
          address = recipientAddress,
          validInvoiceInClipboard = props.validInvoiceInClipboard,
          showToolbarIcons = showToolbarIcons,
          onBack = props.onExit,
          onRecipientEntered = { enteredRecipientAddress ->
            onStateChange(
              EnteringAmountUiState(
                recipientAddress = enteredRecipientAddress,
                transferMoney = defaultAmountEntryAmount
              )
            )
          },
          onScanQrCodeClick = {
            setDynamicIslandQrScannerClosing(false)
            onStateChange(
              if (permissionUiStateMachine.isImplemented) {
                RequestingCameraUiState(recipientAddress = recipientAddress)
              } else {
                ScanningQrCodeUiState(recipientAddress = recipientAddress)
              }
            )
          },
          onGoToUtxoConsolidation = props.onGoToUtxoConsolidation
        )
    )

  @Composable
  private fun scanningRecipientAddressModel(
    state: ScanningQrCodeUiState,
    props: SendUiProps,
    defaultAmountEntryAmount: Money,
    isDynamicIslandQrScannerClosing: () -> Boolean,
    setDynamicIslandQrScannerClosing: (Boolean) -> Unit,
    onStateChange: (SendUiState) -> Unit,
  ): ScreenModel {
    val usesDynamicIslandPortal = usesDynamicIslandQrScannerPortalProvider()
    val recipientAddressBodyModel =
      recipientAddressBodyModel(
        recipientAddress = state.recipientAddress,
        showToolbarIcons = state.isAddressSheetExpanded,
        props = props,
        defaultAmountEntryAmount = defaultAmountEntryAmount,
        setDynamicIslandQrScannerClosing = setDynamicIslandQrScannerClosing,
        onStateChange = onStateChange
      )

    fun expandAddressSheet() {
      onStateChange(state.copy(isAddressSheetExpanded = true))
    }

    fun exitQrScanner() {
      setDynamicIslandQrScannerClosing(false)
      onStateChange(SelectingRecipientUiState(recipientAddress = state.recipientAddress))
    }

    val closeDynamicIslandQrScanner = {
      closeDynamicIslandQrScanner(
        state = state,
        isDynamicIslandQrScannerClosing = isDynamicIslandQrScannerClosing,
        setDynamicIslandQrScannerClosing = setDynamicIslandQrScannerClosing,
        onStateChange = onStateChange
      )
    }

    val onEnterAddressClick =
      if (usesDynamicIslandPortal) {
        closeDynamicIslandQrScanner
      } else {
        ::expandAddressSheet
      }
    val onCloseQrScanner =
      if (usesDynamicIslandPortal) {
        closeDynamicIslandQrScanner
      } else {
        ::exitQrScanner
      }

    val qrCodeScreenModel =
      qrCodeScreenModel(
        state = state,
        props = props,
        usesDynamicIslandPortal = usesDynamicIslandPortal,
        defaultAmountEntryAmount = defaultAmountEntryAmount,
        isDynamicIslandQrScannerClosing = isDynamicIslandQrScannerClosing,
        onEnterAddressClick = onEnterAddressClick,
        onCloseQrScanner = onCloseQrScanner,
        onStateChange = onStateChange
      )

    if (usesDynamicIslandPortal) {
      return DynamicIslandQrScannerPortalBodyModel(
        recipientAddressBodyModel = recipientAddressBodyModel,
        qrScannerScreenModel = qrCodeScreenModel,
        isClosing = state.isClosingDynamicIslandQrScanner,
        onClose = closeDynamicIslandQrScanner,
        onClosed = ::exitQrScanner
      ).asModalFullScreen()
    }

    return embeddedRecipientAddressQrModel(
      qrCodeScreenModel = requireNotNull(qrCodeScreenModel),
      recipientAddressBodyModel = recipientAddressBodyModel,
      state = state,
      expandAddressSheet = ::expandAddressSheet,
      exitQrScanner = ::exitQrScanner
    )
  }

  @Composable
  private fun qrCodeScreenModel(
    state: ScanningQrCodeUiState,
    props: SendUiProps,
    usesDynamicIslandPortal: Boolean,
    defaultAmountEntryAmount: Money,
    isDynamicIslandQrScannerClosing: () -> Boolean,
    onEnterAddressClick: () -> Unit,
    onCloseQrScanner: () -> Unit,
    onStateChange: (SendUiState) -> Unit,
  ): ScreenModel? {
    if (state.isClosingDynamicIslandQrScanner) return null

    return bitcoinQrCodeUiScanStateMachine.model(
      props =
        BitcoinQrCodeScanUiProps(
          validInvoiceInClipboard = props.validInvoiceInClipboard,
          showActionButtons = usesDynamicIslandPortal,
          onEnterAddressClick = onEnterAddressClick,
          onClose = onCloseQrScanner,
          onRecipientScanned = { address ->
            enterAmountFromQrScan(
              address = address,
              transferMoney = defaultAmountEntryAmount,
              usesDynamicIslandPortal = usesDynamicIslandPortal,
              isDynamicIslandQrScannerClosing = isDynamicIslandQrScannerClosing,
              onStateChange = onStateChange
            )
          },
          onInvoiceScanned = { invoice ->
            enterAmountFromQrScan(
              address = invoice.address,
              transferMoney = invoice.amount ?: defaultAmountEntryAmount,
              usesDynamicIslandPortal = usesDynamicIslandPortal,
              isDynamicIslandQrScannerClosing = isDynamicIslandQrScannerClosing,
              onStateChange = onStateChange
            )
          },
          onGoToUtxoConsolidation = props.onGoToUtxoConsolidation
        )
    )
  }

  private fun enterAmountFromQrScan(
    address: BitcoinAddress,
    transferMoney: Money,
    usesDynamicIslandPortal: Boolean,
    isDynamicIslandQrScannerClosing: () -> Boolean,
    onStateChange: (SendUiState) -> Unit,
  ) {
    if (usesDynamicIslandPortal && isDynamicIslandQrScannerClosing()) return
    if (usesDynamicIslandPortal) {
      onDynamicIslandQrScanSuccess()
    }
    onStateChange(
      EnteringAmountUiState(
        recipientAddress = address,
        transferMoney = transferMoney
      )
    )
  }

  private fun closeDynamicIslandQrScanner(
    state: ScanningQrCodeUiState,
    isDynamicIslandQrScannerClosing: () -> Boolean,
    setDynamicIslandQrScannerClosing: (Boolean) -> Unit,
    onStateChange: (SendUiState) -> Unit,
  ) {
    if (isDynamicIslandQrScannerClosing()) return
    setDynamicIslandQrScannerClosing(true)
    onStateChange(state.copy(isClosingDynamicIslandQrScanner = true))
  }

  private fun embeddedRecipientAddressQrModel(
    qrCodeScreenModel: ScreenModel,
    recipientAddressBodyModel: BodyModel,
    state: ScanningQrCodeUiState,
    expandAddressSheet: () -> Unit,
    exitQrScanner: () -> Unit,
  ): ScreenModel {
    val qrCodeBodyModel = qrCodeScreenModel.body
    return if (qrCodeBodyModel is QrCodeScanBodyModel) {
      SendRecipientAddressQrBodyModel(
        scannerBodyModel = qrCodeBodyModel.copy(
          onClose = expandAddressSheet
        ),
        recipientAddressBodyModel = recipientAddressBodyModel,
        addressSheetExpanded = state.isAddressSheetExpanded,
        onAddressSheetExpansionStarted = expandAddressSheet,
        onAddressSheetRestored = exitQrScanner,
        onBack = expandAddressSheet
      ).asModalFullScreen()
    } else {
      qrCodeScreenModel
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
   * Requesting camera to scan QR code.
   */
  data class RequestingCameraUiState(
    val recipientAddress: BitcoinAddress?,
  ) : SendUiState

  /**
   * Customer is scanning a QR code to send funds.
   */
  data class ScanningQrCodeUiState(
    val recipientAddress: BitcoinAddress?,
    val isAddressSheetExpanded: Boolean = false,
    val isClosingDynamicIslandQrScanner: Boolean = false,
  ) : SendUiState

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
