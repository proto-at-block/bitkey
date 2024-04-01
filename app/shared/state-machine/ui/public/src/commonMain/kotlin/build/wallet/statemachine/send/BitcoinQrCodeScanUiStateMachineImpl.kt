package build.wallet.statemachine.send

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.ParsedPaymentData.BIP21
import build.wallet.bitcoin.invoice.ParsedPaymentData.Lightning
import build.wallet.bitcoin.invoice.ParsedPaymentData.Onchain
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.send.BitcoinQrCodeScanUiState.ScanningQrCodeUiState
import build.wallet.statemachine.send.BitcoinQrCodeScanUiState.SelfSendErrorUiState
import build.wallet.statemachine.send.BitcoinQrCodeScanUiState.UnrecognizedErrorUiState
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

class BitcoinQrCodeScanUiStateMachineImpl(
  private val paymentDataParser: PaymentDataParser,
) : BitcoinQrCodeUiScanStateMachine {
  @Composable
  override fun model(props: BitcoinQrCodeScanUiProps): ScreenModel {
    var state: BitcoinQrCodeScanUiState by remember {
      mutableStateOf(ScanningQrCodeUiState(validInvoiceInClipboard = props.validInvoiceInClipboard))
    }

    val showSendToCopiedAddressButton by remember {
      derivedStateOf {
        props.validInvoiceInClipboard.takeIf { it !is Lightning } != null
      }
    }

    var paymentDataToHandle: ParsedPaymentData? by remember { mutableStateOf(null) }
    paymentDataToHandle?.let { paymentData ->
      LaunchedEffect("handling-payment-data", paymentData) {
        handlePaymentDataCaptured(
          spendingWallet = props.spendingWallet,
          paymentData = paymentData,
          onInvalidAddressError = { state = UnrecognizedErrorUiState },
          onSelfSendError = { state = SelfSendErrorUiState },
          onInvoiceScanned = { invoice -> props.onInvoiceScanned(invoice) },
          onRecipientScanned = { address -> props.onRecipientScanned(address) }
        )
      }
    }

    var qrCodeDataToHandle: String? by remember { mutableStateOf(null) }
    qrCodeDataToHandle?.let { qrCodeData ->
      LaunchedEffect("handling-qr-code-data", qrCodeData) {
        paymentDataParser.decode(qrCodeData, props.networkType)
          .onSuccess {
            paymentDataToHandle = it
          }
          .onFailure {
            state = UnrecognizedErrorUiState
          }
      }
    }

    // A function to provide some composition stability when creating model instance.
    fun onSendToCopiedAddressClick() {
      paymentDataToHandle = props.validInvoiceInClipboard
    }

    return when (state) {
      is ScanningQrCodeUiState -> {
        BitcoinQrCodeScanBodyModel(
          showSendToCopiedAddressButton,
          onQrCodeScanned = { qrCodeData ->
            qrCodeDataToHandle = qrCodeData
          },
          onEnterAddressClick = props.onEnterAddressClick,
          onClose = props.onClose,
          onSendToCopiedAddressClick = {
            onSendToCopiedAddressClick()
          }
        ).asFullScreen()
      }
      is UnrecognizedErrorUiState ->
        UnrecognizedErrorScreen(
          onDoneClick = { props.onClose() }
        )

      SelfSendErrorUiState ->
        SelfSendErrorScreen(
          onDoneClick = { props.onClose() }
        )
    }
  }

  @Composable
  private fun UnrecognizedErrorScreen(onDoneClick: () -> Unit): ScreenModel {
    return ErrorFormBodyModel(
      title = "We couldn’t recognize this address",
      subline = "Please double check if we support this address type",
      primaryButton =
        ButtonDataModel(
          text = "Done",
          onClick = onDoneClick
        ),
      eventTrackerScreenId = null
    ).asModalScreen()
  }

  @Composable
  private fun SelfSendErrorScreen(onDoneClick: () -> Unit): ScreenModel {
    return ErrorFormBodyModel(
      title = "You can’t send to your own address",
      primaryButton =
        ButtonDataModel(
          text = "Done",
          onClick = onDoneClick
        ),
      eventTrackerScreenId = null
    ).asModalScreen()
  }

  private suspend fun handlePaymentDataCaptured(
    spendingWallet: SpendingWallet,
    paymentData: ParsedPaymentData,
    onInvalidAddressError: () -> Unit,
    onSelfSendError: () -> Unit,
    onInvoiceScanned: (BitcoinInvoice) -> Unit,
    onRecipientScanned: (BitcoinAddress) -> Unit,
  ) {
    when (paymentData) {
      is BIP21 ->
        if (paymentData.bip21PaymentData.onchainInvoice.address.isSelfSend(spendingWallet)) {
          onSelfSendError()
        } else {
          onInvoiceScanned(paymentData.bip21PaymentData.onchainInvoice)
        }
      is Onchain ->
        if (paymentData.bitcoinAddress.isSelfSend(spendingWallet)) {
          onSelfSendError()
        } else {
          onRecipientScanned(paymentData.bitcoinAddress)
        }
      else -> onInvalidAddressError()
    }
  }

  private suspend fun BitcoinAddress.isSelfSend(spendingWallet: SpendingWallet): Boolean {
    return spendingWallet.isMine(this).get() == true
  }
}

private sealed interface BitcoinQrCodeScanUiState {
  data class ScanningQrCodeUiState(
    val validInvoiceInClipboard: ParsedPaymentData?,
  ) : BitcoinQrCodeScanUiState

  data object UnrecognizedErrorUiState : BitcoinQrCodeScanUiState

  data object SelfSendErrorUiState : BitcoinQrCodeScanUiState
}
