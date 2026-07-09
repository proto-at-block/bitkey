package build.wallet.statemachine.send

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import build.wallet.bitcoin.invoice.ParsedPaymentData.*
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitcoin.invoice.PaymentDataParser.PaymentDataParserError
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.clipboard.Clipboard
import build.wallet.statemachine.core.BodyModel
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@BitkeyInject(ActivityScope::class)
class BitcoinAddressRecipientUiStateMachineImpl(
  private val paymentDataParser: PaymentDataParser,
  private val accountConfigService: AccountConfigService,
  private val bitcoinWalletService: BitcoinWalletService,
  private val clipboard: Clipboard,
  private val appSessionManager: AppSessionManager,
) : BitcoinAddressRecipientUiStateMachine {
  @Composable
  override fun model(props: BitcoinAddressRecipientUiProps): BodyModel {
    var state by remember {
      mutableStateOf(
        State(enteredText = props.address?.address.orEmpty())
      )
    }
    val bitcoinNetwork by remember {
      derivedStateOf {
        accountConfigService.activeOrDefaultConfig().value.bitcoinNetworkType
      }
    }

    val wallet by remember { bitcoinWalletService.spendingWallet() }.collectAsState()

    // Re-read clipboard on each foreground resume so the paste button stays current
    // after the user app-switches to copy an address. Polls the foreground boolean
    // (not the clipboard) to detect transitions — only reads the clipboard once per
    // resume, avoiding Android's "pasted from clipboard" toast spam. We poll instead
    // of observing AppSessionManager.appSessionState because that StateFlow is
    // conflated: fast BACKGROUND→FOREGROUND transitions are invisible to collectors
    // that were suspended during the transition.
    var clipboardPaymentData by remember {
      mutableStateOf(
        clipboard.getPlainTextItem()?.let {
          paymentDataParser.decode(it.data, bitcoinNetwork).get()
        }
      )
    }
    LaunchedEffect(Unit) {
      var wasForegrounded = true
      while (true) {
        delay(500.milliseconds)
        val isNowForegrounded = appSessionManager.isAppForegrounded()
        if (isNowForegrounded && !wasForegrounded) {
          clipboardPaymentData = clipboard.getPlainTextItem()?.let {
            paymentDataParser.decode(it.data, bitcoinNetwork).get()
          }
        }
        wasForegrounded = isNowForegrounded
      }
    }

    val paymentDataParserResult by remember(state.enteredText) {
      derivedStateOf {
        when {
          state.enteredText.isBlank() -> null
          else ->
            paymentDataParser
              .decode(state.enteredText, bitcoinNetwork)
              .map {
                when (val paymentData = it) {
                  is BIP21 -> paymentData.bip21PaymentData.onchainInvoice.address
                  is Onchain -> paymentData.bitcoinAddress
                  else -> null
                }
              }
        }
      }
    }

    var bitcoinAddressResult: BitcoinAddressResult by remember {
      mutableStateOf(BitcoinAddressResult.Invalid)
    }
    LaunchedEffect("check-bitcoin-address-result", paymentDataParserResult) {
      paymentDataParserResult
        ?.onSuccess { bitcoinAddress ->
          bitcoinAddressResult = when {
            bitcoinAddress == null -> BitcoinAddressResult.Invalid
            bitcoinAddress.address.isBlank() -> BitcoinAddressResult.Invalid
            wallet?.isMine(bitcoinAddress)?.get() == true -> BitcoinAddressResult.SelfSend
            else -> BitcoinAddressResult.Success
          }
        }
        ?.onFailure {
          bitcoinAddressResult =
            when (it) {
              is PaymentDataParserError.InvalidNetwork -> BitcoinAddressResult.InvalidNetwork
              else -> BitcoinAddressResult.Invalid
            }
        }
        ?: run {
          bitcoinAddressResult = BitcoinAddressResult.Invalid
        }
    }

    val showPasteButton by remember(state.enteredText, clipboardPaymentData) {
      derivedStateOf {
        clipboardPaymentData.takeIf { it !is Lightning } != null && state.enteredText == ""
      }
    }

    val bitcoinAddressWarningText = when {
      bitcoinAddressResult == BitcoinAddressResult.InvalidNetwork -> "Invalid bitcoin address"
      else -> null
    }

    return BitcoinRecipientAddressScreenModel(
      enteredText = state.enteredText,
      warningText = bitcoinAddressWarningText,
      onEnteredTextChanged = { enteredText ->
        state = state.copy(enteredText = enteredText)
      },
      showPasteButton = showPasteButton,
      onBack = props.onBack,
      onContinueClick =
        paymentDataParserResult?.get()
          .takeIf { it != null && bitcoinAddressResult == BitcoinAddressResult.Success }
          ?.let {
            { props.onRecipientEntered(it) }
          },
      onScanQrCodeClick = props.onScanQrCodeClick,
      onPasteButtonClick = {
        // Read clipboard fresh on click so paste always reflects the current system
        // clipboard, not a stale snapshot captured when the send flow was entered.
        val freshPaymentData = clipboard.getPlainTextItem()?.let {
          paymentDataParser.decode(it.data, bitcoinNetwork).get()
        }
        when (freshPaymentData) {
          is BIP21 ->
            state =
              state.copy(enteredText = freshPaymentData.bip21PaymentData.onchainInvoice.address.address)

          is Onchain -> state = state.copy(enteredText = freshPaymentData.bitcoinAddress.address)
          else -> {}
        }
      },
      showSelfSendWarningWithRedirect = bitcoinAddressResult == BitcoinAddressResult.SelfSend,
      onGoToUtxoConsolidation = props.onGoToUtxoConsolidation,
      showToolbarIcons = props.showToolbarIcons
    )
  }

  /**
   * Represents states when parsing a bitcoin address.
   */
  private sealed interface BitcoinAddressResult {
    /**
     * The address has not been entered or it has an error that is not explicitly handled.
     */
    data object Invalid : BitcoinAddressResult

    /**
     * The address belongs to a different network.
     */
    data object InvalidNetwork : BitcoinAddressResult

    /**
     * The address belongs to the user's wallet.
     */
    data object SelfSend : BitcoinAddressResult

    /**
     * The address was successfully parsed and can be used for sending.
     */
    data object Success : BitcoinAddressResult
  }

  private data class State(
    val enteredText: String,
  )
}
