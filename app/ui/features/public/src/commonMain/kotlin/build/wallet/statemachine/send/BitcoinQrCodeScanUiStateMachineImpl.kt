package build.wallet.statemachine.send

import androidx.compose.runtime.*
import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.ParsedPaymentData
import build.wallet.bitcoin.invoice.ParsedPaymentData.*
import build.wallet.bitcoin.invoice.PaymentDataParser
import build.wallet.bitcoin.transactions.BitcoinWalletService
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.send.BitcoinQrCodeScanResult.*
import build.wallet.statemachine.send.BitcoinQrCodeScanUiState.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.get
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@BitkeyInject(ActivityScope::class)
class BitcoinQrCodeScanUiStateMachineImpl(
  private val paymentDataParser: PaymentDataParser,
  private val bitcoinWalletService: BitcoinWalletService,
  private val accountService: AccountService,
  private val haptics: Haptics,
) : BitcoinQrCodeUiScanStateMachine {
  @Composable
  override fun model(props: BitcoinQrCodeScanUiProps): ScreenModel {
    var state: BitcoinQrCodeScanUiState by remember {
      mutableStateOf(ScanningQrCodeUiState)
    }

    val showSendToCopiedAddressButton =
      props.validInvoiceInClipboard != null && props.validInvoiceInClipboard !is Lightning

    val spendingWallet = remember { bitcoinWalletService.spendingWallet() }
      .collectAsState()
      .value

    var paymentDataToHandle: PaymentDataToHandle? by remember { mutableStateOf(null) }

    fun handleSuccessfulScanResult(result: BitcoinQrCodeScanSuccess) {
      when (result) {
        is ScannedInvoice -> props.onInvoiceScanned(result.invoice)
        is ScannedRecipient -> props.onRecipientScanned(result.address)
      }
    }

    val currentState = state
    LaunchedEffect("scan-success-feedback", currentState) {
      if (currentState !is ScanSuccessUiState) {
        return@LaunchedEffect
      }
      haptics.vibrate(HapticsEffect.Success)
      delay(ScanSuccessFeedbackDuration)
      handleSuccessfulScanResult(currentState.result)
    }

    if (paymentDataToHandle != null && spendingWallet != null) {
      LaunchedEffect("handling-payment-data", paymentDataToHandle) {
        val paymentData = requireNotNull(paymentDataToHandle)
        when (
          val result = handlePaymentDataCaptured(
            spendingWallet = spendingWallet,
            paymentData = paymentData.parsedPaymentData
          )
        ) {
          InvalidAddress -> state = UnrecognizedErrorUiState
          SelfSend -> state = SelfSendErrorUiState
          is BitcoinQrCodeScanSuccess -> {
            if (paymentData.showScanSuccessFeedback) {
              state = ScanSuccessUiState(result)
            } else {
              handleSuccessfulScanResult(result)
            }
          }
        }
        paymentDataToHandle = null
      }
    }

    var qrCodeDataToHandle: String? by remember { mutableStateOf(null) }
    qrCodeDataToHandle?.let { qrCodeData ->
      LaunchedEffect("handling-qr-code-data", qrCodeData) {
        coroutineBinding {
          val account = accountService.getAccount<FullAccount>().bind()
          paymentDataParser.decode(qrCodeData, account.config.bitcoinNetworkType).bind()
        }
          .onSuccess {
            paymentDataToHandle = PaymentDataToHandle(
              parsedPaymentData = it,
              showScanSuccessFeedback = true
            )
          }
          .onFailure { state = UnrecognizedErrorUiState }
      }
    }

    return when (currentState) {
      ScanningQrCodeUiState,
      is ScanSuccessUiState -> {
        BitcoinQrCodeScanBodyModel(
          showSendToCopiedAddressButton = showSendToCopiedAddressButton,
          showActionButtons = props.showActionButtons,
          isScanSuccess = currentState is ScanSuccessUiState,
          onQrCodeScanned = { qrCodeData ->
            if (state == ScanningQrCodeUiState) {
              qrCodeDataToHandle = qrCodeData
            }
          },
          onEnterAddressClick = props.onEnterAddressClick,
          onClose = props.onClose,
          onSendToCopiedAddressClick = {
            props.validInvoiceInClipboard?.let {
              paymentDataToHandle = PaymentDataToHandle(
                parsedPaymentData = it,
                showScanSuccessFeedback = false
              )
            }
          }
        ).asFullScreen()
      }
      UnrecognizedErrorUiState ->
        UnrecognizedErrorScreen(
          onDoneClick = {
            qrCodeDataToHandle = null
            paymentDataToHandle = null
            state = ScanningQrCodeUiState
          }
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
      errorData = ErrorData(
        segment = SendAppSegment,
        actionDescription = "Scanning bitcoin QR code",
        cause = unrecognizedQrCodeError
      ),
      eventTrackerScreenId = null
    ).asModalScreen()
  }

  @Composable
  private fun SelfSendErrorScreen(
    onDoneClick: () -> Unit,
    onGoToUtxoConsolidation: () -> Unit,
  ): ScreenModel {
    return SelfSendErrorBodyModel(
      onDoneClick = onDoneClick,
      onGoToUtxoConsolidation = onGoToUtxoConsolidation
    ).asModalScreen()
  }

  private data class SelfSendErrorBodyModel(
    val onDoneClick: () -> Unit,
    val onGoToUtxoConsolidation: () -> Unit,
  ) : FormBodyModel(
      id = null,
      onBack = onDoneClick,
      toolbar = ToolbarModel(
        leadingAccessory = BackAccessory(onClick = onDoneClick)
      ),
      header = FormHeaderModel(
        icon = LargeIconWarningFilled,
        headline = "This is your Bitkey wallet address",
        sublineModel = LabelModel.LinkSubstringModel.from(
          string = "The address you entered belongs to this Bitkey wallet. Enter an external address" +
            " to transfer funds." +
            "\n\n" +
            "For UTXO consolidation, go to UTXO Consolidation in Settings.",
          substringToOnClick = mapOf("UTXO Consolidation" to onGoToUtxoConsolidation),
          underline = true,
          bold = false,
          color = LabelModel.Color.UNSPECIFIED
        )
      ),
      primaryButton = ButtonModel(
        text = "Done",
        onClick = StandardClick(onDoneClick),
        size = Footer
      ),
      errorData = ErrorData(
        segment = SendAppSegment,
        actionDescription = "Scanning bitcoin QR code",
        cause = selfSendQrCodeError
      )
    )

  private suspend fun handlePaymentDataCaptured(
    spendingWallet: SpendingWallet,
    paymentData: ParsedPaymentData,
  ): BitcoinQrCodeScanResult {
    return when (paymentData) {
      is BIP21 ->
        if (paymentData.bip21PaymentData.onchainInvoice.address.isSelfSend(spendingWallet)) {
          SelfSend
        } else {
          ScannedInvoice(paymentData.bip21PaymentData.onchainInvoice)
        }
      is Onchain ->
        if (paymentData.bitcoinAddress.isSelfSend(spendingWallet)) {
          SelfSend
        } else {
          ScannedRecipient(paymentData.bitcoinAddress)
        }
      else -> InvalidAddress
    }
  }

  private suspend fun BitcoinAddress.isSelfSend(spendingWallet: SpendingWallet): Boolean {
    return spendingWallet.isMine(this).get() == true
  }
}

private sealed interface BitcoinQrCodeScanUiState {
  data object ScanningQrCodeUiState : BitcoinQrCodeScanUiState

  data class ScanSuccessUiState(
    val result: BitcoinQrCodeScanSuccess,
  ) : BitcoinQrCodeScanUiState

  data object UnrecognizedErrorUiState : BitcoinQrCodeScanUiState

  data object SelfSendErrorUiState : BitcoinQrCodeScanUiState
}

private sealed interface BitcoinQrCodeScanResult {
  data class ScannedRecipient(val address: BitcoinAddress) : BitcoinQrCodeScanSuccess

  data class ScannedInvoice(val invoice: BitcoinInvoice) : BitcoinQrCodeScanSuccess

  data object InvalidAddress : BitcoinQrCodeScanResult

  data object SelfSend : BitcoinQrCodeScanResult
}

private sealed interface BitcoinQrCodeScanSuccess : BitcoinQrCodeScanResult

private data class PaymentDataToHandle(
  val parsedPaymentData: ParsedPaymentData,
  val showScanSuccessFeedback: Boolean,
)

private val ScanSuccessFeedbackDuration = 150.milliseconds

private val unrecognizedQrCodeError =
  IllegalArgumentException("Scanned QR code was not a supported bitcoin payment target")

private val selfSendQrCodeError =
  IllegalArgumentException("Scanned address belongs to this Bitkey wallet")
