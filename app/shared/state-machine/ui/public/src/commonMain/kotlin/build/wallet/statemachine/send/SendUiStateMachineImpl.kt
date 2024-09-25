package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.availability.NetworkReachability
import build.wallet.availability.NetworkReachabilityProvider
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.fees.Fee
import build.wallet.bitcoin.fees.FeeRate
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.ExactAmount
import build.wallet.bitcoin.transactions.BitcoinTransactionSendAmount.SendAll
import build.wallet.bitcoin.transactions.EstimatedTransactionPriority
import build.wallet.bitcoin.transactions.TransactionDetails
import build.wallet.bitkey.factor.SigningFactor
import build.wallet.limit.SpendingLimit
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.Money
import build.wallet.money.currency.Currency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.ExchangeRate
import build.wallet.money.exchange.ExchangeRateService
import build.wallet.platform.permissions.Permission.Camera
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.platform.permissions.PermissionUiProps
import build.wallet.statemachine.platform.permissions.PermissionUiStateMachine
import build.wallet.statemachine.send.SendEntryPoint.SendButton
import build.wallet.statemachine.send.SendEntryPoint.SpeedUp
import build.wallet.statemachine.send.SendUiState.*
import build.wallet.statemachine.send.TransferConfirmationUiProps.Variant
import build.wallet.statemachine.send.fee.FeeSelectionUiProps
import build.wallet.statemachine.send.fee.FeeSelectionUiStateMachine
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.minutes

class SendUiStateMachineImpl(
  private val bitcoinAddressRecipientUiStateMachine: BitcoinAddressRecipientUiStateMachine,
  private val transferAmountEntryUiStateMachine: TransferAmountEntryUiStateMachine,
  private val transferConfirmationUiStateMachine: TransferConfirmationUiStateMachine,
  private val transferInitiatedUiStateMachine: TransferInitiatedUiStateMachine,
  private val bitcoinQrCodeUiScanStateMachine: BitcoinQrCodeUiScanStateMachine,
  private val permissionUiStateMachine: PermissionUiStateMachine,
  private val feeSelectionUiStateMachine: FeeSelectionUiStateMachine,
  private val exchangeRateService: ExchangeRateService,
  private val clock: Clock,
  private val networkReachabilityProvider: NetworkReachabilityProvider,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
) : SendUiStateMachine {
  @Composable
  @Suppress("CyclomaticComplexMethod")
  override fun model(props: SendUiProps): ScreenModel {
    var uiState by remember {
      mutableStateOf(
        when (val entryPoint = props.entryPoint) {
          SendButton -> SelectingRecipientUiState(recipientAddress = null)
          is SpeedUp ->
            ConfirmingTransferUiState(
              variant =
                Variant.SpeedUp(
                  txid = entryPoint.speedUpTransactionDetails.txid,
                  oldFee = entryPoint.speedUpTransactionDetails.oldFee,
                  // For test accounts, we manually choose a fee rate that is twice the previous
                  // one. This is particularly useful for QA when testing.
                  newFeeRate =
                    if (props.account.config.isTestAccount) {
                      FeeRate(
                        satsPerVByte = entryPoint.speedUpTransactionDetails.oldFee.feeRate.satsPerVByte * 2
                      )
                    } else {
                      entryPoint.newFeeRate
                    }
                ),
              requiredSigner = SigningFactor.Hardware,
              recipientAddress = entryPoint.speedUpTransactionDetails.recipientAddress,
              sendAmount = ExactAmount(entryPoint.speedUpTransactionDetails.sendAmount),
              spendingLimit = entryPoint.spendingLimit,
              fees = entryPoint.fees
            )
        }
      )
    }

    val fiatCurrency by fiatCurrencyPreferenceRepository.fiatCurrencyPreference.collectAsState()

    // On initiating the send flow, we grab and lock in the current exchange rates, so we use
    // the same rates over the duration of the flow. This is null when the exchange rates are not
    // available or are out of date due to the customer being offline or unable to communicate with f8e
    val exchangeRates: ImmutableList<ExchangeRate>? by remember {
      val rates = exchangeRateService.exchangeRates.value.toImmutableList()
      val instant = rates.timeRetrievedForCurrency(fiatCurrency)
      mutableStateOf(
        when {
          // if rates are older than 5 minutes or we cant find any for our fiat currency, we don't
          // use them
          instant == null || instant <= clock.now() - 5.minutes -> null
          else -> rates
        }
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

    val f8eReachabilityState by remember {
      networkReachabilityProvider.f8eReachabilityFlow(
        props.account.config.f8eEnvironment
      )
    }.collectAsState()

    return when (val state = uiState) {
      is SelectingRecipientUiState ->
        bitcoinAddressRecipientUiStateMachine.model(
          props =
            BitcoinAddressRecipientUiProps(
              address = state.recipientAddress,
              networkType = props.account.config.bitcoinNetworkType,
              spendingKeyset = props.account.keybox.activeSpendingKeyset,
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
              when (props.entryPoint) {
                SendButton -> {
                  uiState = SelectingRecipientUiState(recipientAddress = null)
                }
                is SpeedUp -> props.onExit()
              }
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
              networkType = props.account.config.bitcoinNetworkType,
              validInvoiceInClipboard = props.validInvoiceInClipboard,
              onEnterAddressClick = {
                uiState = SelectingRecipientUiState(recipientAddress = null)
              },
              onClose = props.onExit,
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
            exchangeRates = exchangeRates,
            f8eReachability = f8eReachabilityState
          ) { continueParams ->
            uiState = SelectingTransactionPriorityUiState(
              recipientAddress = state.recipientAddress,
              sendAmount = continueParams.sendAmount,
              requiredSigner = continueParams.requiredSigner,
              spendingLimit = continueParams.spendingLimit
            )
          }
        )

      is ConfirmingTransferUiState ->
        transferConfirmationUiStateMachine.model(
          props = TransferConfirmationUiProps(
            transferVariant = state.variant,
            account = props.account,
            recipientAddress = state.recipientAddress,
            sendAmount = state.sendAmount,
            onExit = props.onExit,
            onBack = {
              uiState = EnteringAmountUiState(
                recipientAddress = state.recipientAddress,
                transferMoney =
                  when (val amount = state.sendAmount) {
                    is ExactAmount -> amount.money
                    is SendAll -> FiatMoney.zero(fiatCurrency)
                  }
              )
            },
            requiredSigner = when (f8eReachabilityState) {
              NetworkReachability.REACHABLE -> state.requiredSigner
              NetworkReachability.UNREACHABLE -> SigningFactor.Hardware
            },
            spendingLimit = state.spendingLimit,
            fees = state.fees,
            onTransferFailed = props.onExit,
            exchangeRates = exchangeRates,
            onTransferInitiated = { psbt, priority ->
              uiState = TransferInitiatedUiState(
                recipientAddress = state.recipientAddress,
                transferMoney = BitcoinMoney.sats(psbt.amountSats.toBigInteger()),
                feeBitcoinAmount = psbt.fee,
                estimatedTransactionPriority = priority,
                confirmationVariant = state.variant
              )
            }
          )
        )

      is TransferInitiatedUiState ->
        transferInitiatedUiStateMachine.model(
          props =
            TransferInitiatedUiProps(
              recipientAddress = state.recipientAddress,
              transactionDetails =
                when (state.confirmationVariant) {
                  is Variant.Regular -> TransactionDetails.Regular(
                    transferAmount = state.transferMoney,
                    feeAmount = state.feeBitcoinAmount,
                    estimatedTransactionPriority = state.estimatedTransactionPriority
                  )
                  is Variant.SpeedUp -> TransactionDetails.SpeedUp(
                    transferAmount = state.transferMoney,
                    oldFeeAmount = state.confirmationVariant.oldFee.amount,
                    feeAmount = state.feeBitcoinAmount
                  )
                },
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
                        is SendAll -> FiatMoney.zero(fiatCurrency)
                      }
                  )
              },
              onContinue = { priority, fees ->
                uiState =
                  ConfirmingTransferUiState(
                    variant = Variant.Regular(selectedPriority = priority),
                    recipientAddress = state.recipientAddress,
                    requiredSigner = state.requiredSigner,
                    spendingLimit = state.spendingLimit,
                    sendAmount = state.sendAmount,
                    fees = fees
                  )
              }
            )
        ).asModalFullScreen()
    }
  }

  private fun ImmutableList<ExchangeRate>.timeRetrievedForCurrency(currency: Currency): Instant? {
    return firstOrNull { it.fromCurrency == currency.textCode || it.toCurrency == currency.textCode }
      ?.timeRetrieved
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
    val requiredSigner: SigningFactor,
    val recipientAddress: BitcoinAddress,
    val sendAmount: BitcoinTransactionSendAmount,
    val spendingLimit: SpendingLimit?,
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
    val variant: Variant,
    val requiredSigner: SigningFactor,
    val recipientAddress: BitcoinAddress,
    val sendAmount: BitcoinTransactionSendAmount,
    val spendingLimit: SpendingLimit?,
    val fees: ImmutableMap<EstimatedTransactionPriority, Fee>,
  ) : SendUiState

  /**
   * Customer successfully initiated transfer.
   */
  data class TransferInitiatedUiState(
    val confirmationVariant: Variant,
    val recipientAddress: BitcoinAddress,
    val transferMoney: BitcoinMoney,
    val feeBitcoinAmount: BitcoinMoney,
    val estimatedTransactionPriority: EstimatedTransactionPriority,
  ) : SendUiState
}
