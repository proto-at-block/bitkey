package build.wallet.statemachine.receive

import androidx.compose.runtime.*
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.sharing.SharingManager
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.Icon.SmallIconCopy
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.statemachine.root.RestoreCopyAddressStateDelay
import build.wallet.ui.theme.Theme
import build.wallet.ui.theme.ThemePreferenceService
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.delay

const val FOREGROUND_LIGHT_COLOR = "000000"
const val FOREGROUND_DARK_COLOR = "e2e2e2"
const val BACKGROUND_LIGHT_COLOR = "ffffff"
const val BACKGROUND_DARK_COLOR = "000000"

@BitkeyInject(ActivityScope::class)
class AddressQrCodeUiStateMachineImpl(
  private val clipboard: Clipboard,
  private val restoreCopyAddressStateDelay: RestoreCopyAddressStateDelay,
  private val sharingManager: SharingManager,
  private val bitcoinInvoiceUrlEncoder: BitcoinInvoiceUrlEncoder,
  private val bitcoinAddressService: BitcoinAddressService,
  private val themePreferenceService: ThemePreferenceService,
) : AddressQrCodeUiStateMachine {
  @Composable
  override fun model(props: AddressQrCodeUiProps): BodyModel {
    var state: State by remember { mutableStateOf(State.LoadingAddressUiState) }

    val theme by remember {
      themePreferenceService.theme()
    }.collectAsState(Theme.LIGHT)

    val qrCodeColor by remember(theme) {
      when (theme) {
        Theme.LIGHT -> mutableStateOf(FOREGROUND_LIGHT_COLOR)
        Theme.DARK -> mutableStateOf(FOREGROUND_DARK_COLOR)
      }
    }

    val qrCodeBackgroundColor by remember(theme) {
      when (theme) {
        Theme.LIGHT -> mutableStateOf(BACKGROUND_LIGHT_COLOR)
        Theme.DARK -> mutableStateOf(BACKGROUND_DARK_COLOR)
      }
    }

    val qrCodeUrlString by remember(qrCodeColor, qrCodeBackgroundColor, state) {
      when (val address = state.address) {
        null -> mutableStateOf(null)
        else -> mutableStateOf("https://api.cash.app/qr/btc/${address.address}?currency=btc&logoColor=$qrCodeColor&bg=$qrCodeBackgroundColor&fg=$qrCodeColor&rounded=true&size=2000&errorCorrection=2")
      }
    }

    when (val currentState = state) {
      is State.LoadingAddressUiState -> {
        LaunchedEffect("loading-address") {
          bitcoinAddressService.generateAddress()
            .onSuccess { address ->
              state =
                State.AddressLoadedUiState(
                  address = address,
                  copyStatus = State.CopyStatus.Ready,
                  addressInvoice = bitcoinInvoiceUrlEncoder.encode(BitcoinInvoice(address = address)),
                  chunkedAddress = address.chunkedAddress()
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
      is State.LoadingAddressUiState, is State.AddressLoadedUiState ->
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState
          },
          content =
            QrCode(
              address = state.address?.address,
              addressQrImageUrl = qrCodeUrlString,
              fallbackAddressQrCodeModel =
                state.addressInvoice?.let {
                  QrCodeModel(data = it)
                },
              copyButtonIcon = state.copyStatus.icon(),
              copyButtonLabelText = state.copyStatus.labelText(),
              onCopyClick = {
                state.address?.let { address ->
                  clipboard.setItem(item = PlainText(data = address.address))
                  state =
                    State.AddressLoadedUiState(
                      address = address,
                      addressInvoice = bitcoinInvoiceUrlEncoder.encode(BitcoinInvoice(address = address)),
                      copyStatus = State.CopyStatus.Copied
                    )
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
        AddressQrCodeBodyModel(
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
