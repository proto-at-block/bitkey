package build.wallet.statemachine.receivev2

import androidx.compose.runtime.*
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.sharing.SharingManager
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.Icon.SmallIconCopy
import build.wallet.statemachine.qr.QrCodeService
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receive.AddressQrCodeUiProps
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel.Content.Error
import build.wallet.statemachine.receivev2.AddressQrCodeV2BodyModel.Content.QrCode
import build.wallet.statemachine.root.RestoreCopyAddressStateDelay
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay

@BitkeyInject(ActivityScope::class)
class AddressQrCodeUiV2StateMachineImpl(
  private val clipboard: Clipboard,
  private val restoreCopyAddressStateDelay: RestoreCopyAddressStateDelay,
  private val sharingManager: SharingManager,
  private val bitcoinInvoiceUrlEncoder: BitcoinInvoiceUrlEncoder,
  private val bitcoinAddressService: BitcoinAddressService,
  private val qrCodeService: QrCodeService,
) : AddressQrCodeUiStateMachine {
  @Composable
  override fun model(props: AddressQrCodeUiProps): BodyModel {
    var state: State by remember { mutableStateOf(State.LoadingAddressUiState) }

    when (val currentState = state) {
      is State.LoadingAddressUiState -> {
        LaunchedEffect("loading-address") {
          bitcoinAddressService.generateAddress()
            .onSuccess { address ->
              val qrCodeResult = qrCodeService.generateQrCode(address.address)
                .logFailure { "Error generating QR code." }
              val qrCodeState = if (qrCodeResult.isOk) {
                QrCodeState.Success(qrCodeResult.value)
              } else {
                QrCodeState.Error
              }

              state = State.AddressLoadedUiState(
                address = address,
                copyStatus = State.CopyStatus.Ready,
                addressInvoice = bitcoinInvoiceUrlEncoder.encode(
                  invoice = BitcoinInvoice(address = address)
                ),
                chunkedAddress = address.chunkedAddress(),
                qrCodeState = qrCodeState
              )
            }
            .onFailure {
              state = State.ErrorLoadingAddressUiState
            }
        }
      }

      is State.AddressLoadedUiState -> {
        if (state.copyStatus == State.CopyStatus.Copied) {
          LaunchedEffect("restore-copy-state") {
            delay(restoreCopyAddressStateDelay.value)
            state = currentState.copy(copyStatus = State.CopyStatus.Ready)
          }
        }
      }

      else -> Unit
    }

    return when (state) {
      is State.AddressLoadedUiState, State.LoadingAddressUiState ->
        AddressQrCodeV2BodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState
          },
          content =
            QrCode(
              address = state.address?.address,
              qrCodeState = state.qrCodeState,
              copyButtonIcon = state.copyStatus.icon(),
              copyButtonLabelText = state.copyStatus.labelText(),
              onCopyClick = {
                (state as? State.AddressLoadedUiState)?.let { currentState ->
                  clipboard.setItem(item = PlainText(data = currentState.address.address))
                  state = currentState.copy(copyStatus = State.CopyStatus.Copied)
                }
              },
              onShareClick = {
                state.address?.let {
                  sharingManager.shareText(
                    text = it.address,
                    title = "Bitcoin Address",
                    completion = null
                  )
                }
              }
            )
        )

      is State.ErrorLoadingAddressUiState ->
        AddressQrCodeV2BodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState
          },
          content =
            Error(
              title = "We couldn’t create an address",
              subline = "We are looking into this. Please try again later."
            )
        )
    }
  }

  private sealed class State {
    open val address: BitcoinAddress? = null
    open val addressInvoice: String? = null
    open val qrCodeState: QrCodeState = QrCodeState.Loading
    open val chunkedAddress: String? = null
    open val copyStatus: CopyStatus = CopyStatus.Ready

    /**
     * Indicates that we are currently generating a new address, and re-rendering a QR code.
     */
    data object LoadingAddressUiState : State()

    /**
     * Indicates that we have generated an appropriate receiving address and are rendering it in a
     * QR code.
     *
     * @property [address] - receiving address that will be encoded in a QR code.
     */
    data class AddressLoadedUiState(
      override val address: BitcoinAddress,
      override val addressInvoice: String,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = address.chunkedAddress(),
      override val copyStatus: CopyStatus,
    ) : State()

    data object ErrorLoadingAddressUiState : State()

    enum class CopyStatus {
      Ready,
      Copied,
      ;

      fun icon(): Icon {
        return when (this) {
          Ready -> SmallIconCopy
          Copied -> SmallIconCheckFilled
        }
      }

      fun labelText(): String {
        return when (this) {
          Ready -> "Copy"
          Copied -> "Copied"
        }
      }
    }
  }
}
