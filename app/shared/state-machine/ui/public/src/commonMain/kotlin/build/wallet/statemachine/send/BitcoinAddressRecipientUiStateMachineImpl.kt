package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.ParsedPaymentData.BIP21
import build.wallet.bitcoin.invoice.ParsedPaymentData.Lightning
import build.wallet.bitcoin.invoice.ParsedPaymentData.Onchain
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitcoin.invoice.PaymentDataParser.PaymentDataParserError.InvalidNetwork
import build.wallet.bitcoin.wallet.WatchingWallet
import build.wallet.keybox.wallet.KeysetWalletProvider
import build.wallet.statemachine.core.BodyModel
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class BitcoinAddressRecipientUiStateMachineImpl(
  private val keysetWalletProvider: KeysetWalletProvider,
  private val paymentDataParser: PaymentDataParser,
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

    var bitcoinAddressWarningText: String? by remember { mutableStateOf(null) }
    LaunchedEffect("update-address-warning-text", paymentDataParserResult) {
      paymentDataParserResult
        ?.onSuccess { bitcoinAddress ->
          bitcoinAddressWarningText =
            when {
              bitcoinAddress == null -> null
              bitcoinAddress.address.isBlank() -> null
              wallet?.isMine(bitcoinAddress)
                ?.get() == true -> "Sorry, you can’t send to your own address"

              else -> null
            }
        }
        ?.onFailure {
          bitcoinAddressWarningText =
            when (it) {
              is InvalidNetwork -> "Invalid bitcoin address"
              else -> null
            }
        }
        ?: run {
          bitcoinAddressWarningText = null
        }
    }

    val showPasteButton by remember(state.enteredText) {
      derivedStateOf {
        props.validInvoiceInClipboard.takeIf { it !is Lightning } != null && state.enteredText == ""
      }
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
          .takeIf { it != null && bitcoinAddressWarningText == null }
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
      }
    )
  }

  private data class State(
    val enteredText: String,
    val validInvoiceInClipboard: ParsedPaymentData?,
  )
}
