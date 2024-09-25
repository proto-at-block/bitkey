package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.ParsedPaymentData.*
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitcoin.invoice.PaymentDataParser.PaymentDataParserError
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.feature.flags.UtxoConsolidationFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.send.BitcoinAddressRecipientUiStateMachineImpl.AddressError.SelfSend
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class BitcoinAddressRecipientUiStateMachineImpl(
  private val keysetWalletProvider: KeysetWalletProvider,
  private val paymentDataParser: PaymentDataParser,
  private val utxoConsolidationFeatureFlag: UtxoConsolidationFeatureFlag,
) : BitcoinAddressRecipientUiStateMachine {
  @Composable
  override fun model(props: BitcoinAddressRecipientUiProps): BodyModel {
    var state by remember {
      mutableStateOf(
        State(
          enteredText = props.address?.address.orEmpty(),
          validInvoiceInClipboard = props.validInvoiceInClipboard
        )
      )
    }

    var wallet: WatchingWallet? by remember {
      mutableStateOf(null)
    }

    LaunchedEffect("load-wallet") {
      wallet = keysetWalletProvider.getWatchingWallet(keyset = props.spendingKeyset).get()
    }

    val paymentDataParserResult by remember(state.enteredText) {
      derivedStateOf {
        when {
          state.enteredText.isBlank() -> null
          else ->
            paymentDataParser
              .decode(state.enteredText, props.networkType)
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

    var addressError: AddressError? by remember { mutableStateOf(null) }
    LaunchedEffect("update-address-warning-text", paymentDataParserResult) {
      paymentDataParserResult
        ?.onSuccess { bitcoinAddress ->
          addressError = when {
            bitcoinAddress == null -> null
            bitcoinAddress.address.isBlank() -> null
            wallet?.isMine(bitcoinAddress)?.get() == true -> SelfSend
            else -> null
          }
        }
        ?.onFailure {
          addressError =
            when (it) {
              is PaymentDataParserError.InvalidNetwork -> AddressError.InvalidNetwork
              else -> null
            }
        }
        ?: run {
          addressError = null
        }
    }

    val showPasteButton by remember(state.enteredText) {
      derivedStateOf {
        props.validInvoiceInClipboard.takeIf { it !is Lightning } != null && state.enteredText == ""
      }
    }

    val bitcoinAddressWarningText = when {
      addressError == AddressError.InvalidNetwork -> "Invalid bitcoin address"
      addressError == SelfSend && !utxoConsolidationFeatureFlag.isEnabled() -> "You can’t send to your own address"
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
          .takeIf { it != null && addressError == null }
          ?.let {
            { props.onRecipientEntered(it) }
          },
      onScanQrCodeClick = props.onScanQrCodeClick,
      onPasteButtonClick = {
        state.validInvoiceInClipboard.let { parsedPaymentData ->
          when (parsedPaymentData) {
            is BIP21 ->
              state =
                state.copy(enteredText = parsedPaymentData.bip21PaymentData.onchainInvoice.address.address)

            is Onchain -> state = state.copy(enteredText = parsedPaymentData.bitcoinAddress.address)
            else -> {}
          }
        }
      },
      showSelfSendWarningWithRedirect = addressError == SelfSend && utxoConsolidationFeatureFlag.isEnabled(),
      onGoToUtxoConsolidation = props.onGoToUtxoConsolidation
    )
  }

  /**
   * Represents errors when parsing a bitcoin address.
   */
  private sealed interface AddressError {
    data object InvalidNetwork : AddressError

    data object SelfSend : AddressError
  }

  private data class State(
    val enteredText: String,
    val validInvoiceInClipboard: ParsedPaymentData?,
  )
}
