package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.ParsedPaymentData.*
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.*
import build.wallet.statemachine.send.BitcoinQrCodeScanUiState.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class BitcoinQrCodeScanUiStateMachineImpl(
  private val paymentDataParser: PaymentDataParser,
  private val bitcoinWalletService: BitcoinWalletService,
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

    val spendingWallet = remember { bitcoinWalletService.spendingWallet() }
      .collectAsState()
      .value

    var paymentDataToHandle: ParsedPaymentData? by remember { mutableStateOf(null) }
    if (paymentDataToHandle != null && spendingWallet != null) {
      LaunchedEffect("handling-payment-data", paymentDataToHandle) {
        handlePaymentDataCaptured(
          spendingWallet = spendingWallet,
          paymentData = requireNotNull(paymentDataToHandle),
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
          onDoneClick = props.onClose
        )

      SelfSendErrorUiState ->
        SelfSendErrorScreen(
          onDoneClick = props.onClose,
          onGoToUtxoConsolidation = props.onGoToUtxoConsolidation
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
  private fun SelfSendErrorScreen(
    onDoneClick: () -> Unit,
    onGoToUtxoConsolidation: () -> Unit,
  ): ScreenModel {
    return ErrorFormBodyModelWithOptionalErrorData(
      title = "This is your Bitkey wallet address",
      subline = LabelModel.LinkSubstringModel.from(
        string = "The address you entered belongs to this Bitkey wallet. Enter an external address" +
          " to transfer funds." +
          "\n\n" +
          "For UTXO consolidation, go to UTXO Consolidation in Settings.",
        substringToOnClick = mapOf("UTXO Consolidation" to onGoToUtxoConsolidation),
        underline = true,
        bold = false,
        color = LabelModel.Color.UNSPECIFIED
      ),
      primaryButton = ButtonDataModel(
        text = "Done",
        onClick = onDoneClick
      ),
      toolbar = ToolbarModel(
        leadingAccessory = BackAccessory(onClick = onDoneClick)
      ),
      eventTrackerScreenId = null,
      errorData = null
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
