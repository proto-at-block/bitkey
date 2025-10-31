package build.wallet.statemachine.receive

import androidx.compose.runtime.*
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitcoin.invoice.BitcoinInvoice
import build.wallet.bitcoin.invoice.BitcoinInvoiceUrlEncoder
import build.wallet.compose.collections.emptyImmutableList
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClient
import build.wallet.f8e.partnerships.GetTransferRedirectF8eClient
import build.wallet.f8e.partnerships.RedirectUrlType
import build.wallet.logging.logFailure
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.PartnershipTransactionType
import build.wallet.partnerships.PartnershipTransactionsService
import build.wallet.platform.clipboard.ClipItem.PlainText
import build.wallet.platform.clipboard.Clipboard
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import build.wallet.platform.links.DeepLinkHandler
import build.wallet.platform.sharing.SharingManager
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.Icon.SmallIconCheckFilled
import build.wallet.statemachine.core.Icon.SmallIconCopy
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.qr.QrCodeService
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.statemachine.root.RestoreCopyAddressStateDelay
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import build.wallet.platform.links.AppRestrictions as PlatformAppRestrictions

@BitkeyInject(ActivityScope::class)
class AddressQrCodeUiStateMachineImpl(
  private val clipboard: Clipboard,
  private val restoreCopyAddressStateDelay: RestoreCopyAddressStateDelay,
  private val sharingManager: SharingManager,
  private val bitcoinInvoiceUrlEncoder: BitcoinInvoiceUrlEncoder,
  private val bitcoinAddressService: BitcoinAddressService,
  private val qrCodeService: QrCodeService,
  private val getTransferPartnerListF8eClient: GetTransferPartnerListF8eClient,
  private val getTransferRedirectF8eClient: GetTransferRedirectF8eClient,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val deepLinkHandler: DeepLinkHandler,
  private val haptics: Haptics,
  private val eventTracker: EventTracker,
) : AddressQrCodeUiStateMachine {
  @Composable
  override fun model(props: AddressQrCodeUiProps): BodyModel {
    var state: State by remember {
      mutableStateOf(State.LoadingAddressUiState(emptyImmutableList()))
    }
    val scope = rememberStableCoroutineScope()

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

              state = if (currentState.partners.isEmpty()) {
                // We haven't yet loaded partners, or none were available when we last checked.
                // Try again.
                State.LoadingPartnersUiState(
                  address = address,
                  copyStatus = State.CopyStatus.Ready,
                  addressInvoice = bitcoinInvoiceUrlEncoder.encode(
                    invoice = BitcoinInvoice(address = address)
                  ),
                  chunkedAddress = address.chunkedAddress(),
                  qrCodeState = qrCodeState,
                  partners = emptyImmutableList()
                )
              } else {
                // We've already loaded partners, don't reload the list
                State.AddressLoadedUiState(
                  address = address,
                  copyStatus = State.CopyStatus.Ready,
                  addressInvoice = bitcoinInvoiceUrlEncoder.encode(
                    invoice = BitcoinInvoice(address = address)
                  ),
                  chunkedAddress = address.chunkedAddress(),
                  qrCodeState = qrCodeState,
                  partners = currentState.partners
                )
              }
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

      is State.LoadingPartnerRedirect -> {
        LaunchedEffect("load-partner-redirect") {
          coroutineBinding {
            val redirectResult = getTransferRedirectF8eClient
              .getTransferRedirect(
                fullAccountId = props.account.keybox.fullAccountId,
                f8eEnvironment = props.account.keybox.config.f8eEnvironment,
                partner = currentState.partnerInfo.partnerId.value,
                address = currentState.address
              ).bind()

            val localTransaction = partnershipTransactionsService.create(
              id = redirectResult.redirectInfo.partnerTransactionId,
              partnerInfo = currentState.partnerInfo,
              type = PartnershipTransactionType.TRANSFER
            ).bind()

            localTransaction to redirectResult
          }.onFailure { error ->
            state = State.PartnerRedirectError(
              address = currentState.address,
              addressInvoice = currentState.addressInvoice,
              qrCodeState = currentState.qrCodeState,
              chunkedAddress = currentState.chunkedAddress,
              copyStatus = currentState.copyStatus,
              partnerInfo = currentState.partnerInfo,
              error = error,
              partners = currentState.partners
            )
          }.onSuccess { (localTransaction, result) ->
            when (result.redirectInfo.redirectType) {
              RedirectUrlType.DEEPLINK -> {
                deepLinkHandler.openDeeplink(
                  url = result.redirectInfo.url,
                  appRestrictions = result.redirectInfo.appRestrictions?.let { appRestr ->
                    PlatformAppRestrictions(
                      packageName = appRestr.packageName,
                      minVersion = appRestr.minVersion
                    )
                  }
                )
              }
              RedirectUrlType.WIDGET -> {
                props.onWebLinkOpened(
                  result.redirectInfo.url,
                  currentState.partnerInfo,
                  localTransaction
                )
              }
            }
            state = State.AddressLoadedUiState(
              address = currentState.address,
              addressInvoice = currentState.addressInvoice,
              qrCodeState = currentState.qrCodeState,
              chunkedAddress = currentState.chunkedAddress,
              copyStatus = currentState.copyStatus,
              partners = currentState.partners
            )
          }
        }
      }

      else -> Unit
    }

    return when (val currentState = state) {
      is State.LoadingAddressUiState ->
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState(currentState.partners)
          },
          content =
            QrCode(
              address = null,
              qrCodeState = QrCodeState.Loading,
              partners = currentState.partners,
              onPartnerClick = { /* Disabled during address loading */ },
              copyButtonIcon = Icon.SmallIconCopy,
              copyButtonLabelText = "Copy",
              onCopyClick = {},
              onShareClick = {}
            )
        )

      is State.AddressLoadedUiState ->
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState(currentState.partners)
          },
          content =
            QrCode(
              address = currentState.address.address,
              qrCodeState = currentState.qrCodeState,
              partners = currentState.partners,
              onPartnerClick = { partner ->
                state = State.LoadingPartnerRedirect(
                  address = currentState.address,
                  addressInvoice = currentState.addressInvoice,
                  qrCodeState = currentState.qrCodeState,
                  chunkedAddress = currentState.chunkedAddress,
                  copyStatus = currentState.copyStatus,
                  partnerInfo = partner,
                  partners = currentState.partners
                )
              },
              copyButtonIcon = currentState.copyStatus.icon(),
              copyButtonLabelText = currentState.copyStatus.labelText(),
              onCopyClick = {
                scope.launch {
                  haptics.vibrate(HapticsEffect.MediumClick)
                }
                clipboard.setItem(item = PlainText(data = currentState.address.address))
                state = currentState.copy(copyStatus = State.CopyStatus.Copied)
              },
              onShareClick = {
                sharingManager.shareText(
                  text = currentState.address.address,
                  title = "Bitcoin Address",
                  completion = null
                )
              }
            )
        )

      is State.LoadingPartnersUiState -> {
        LaunchedEffect("load-partners") {
          getTransferPartnerListF8eClient
            .getTransferPartners(
              fullAccountId = props.account.keybox.fullAccountId,
              f8eEnvironment = props.account.keybox.config.f8eEnvironment
            )
            .onSuccess { response ->
              val transferPartners = response.partnerList.toImmutableList()
              transferPartners.forEach { partner ->
                eventTracker.track(
                  action = Action.ACTION_APP_PARTNERSHIPS_VIEWED_TRANSFER_PARTNER,
                  context = PartnerEventTrackerScreenIdContext(partner)
                )
              }
              state = State.AddressLoadedUiState(
                address = currentState.address,
                addressInvoice = currentState.addressInvoice,
                qrCodeState = currentState.qrCodeState,
                chunkedAddress = currentState.chunkedAddress,
                copyStatus = currentState.copyStatus,
                partners = transferPartners
              )
            }
            .logFailure { "Error loading transfer partners for receive screen" }
            .onFailure { error ->
              // TODO W-14880 Display some message to user informing them partners load failed
              state = State.AddressLoadedUiState(
                address = currentState.address,
                addressInvoice = currentState.addressInvoice,
                qrCodeState = currentState.qrCodeState,
                chunkedAddress = currentState.chunkedAddress,
                copyStatus = currentState.copyStatus,
                partners = emptyImmutableList()
              )
            }
        }
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState(currentState.partners)
          },
          content =
            QrCode(
              address = currentState.address.address,
              qrCodeState = currentState.qrCodeState,
              partners = currentState.partners,
              onPartnerClick = { /* Disable during loading */ },
              copyButtonIcon = currentState.copyStatus.icon(),
              copyButtonLabelText = currentState.copyStatus.labelText(),
              onCopyClick = {
                scope.launch {
                  haptics.vibrate(HapticsEffect.MediumClick)
                }
                clipboard.setItem(item = PlainText(data = currentState.address.address))
                state = currentState.copy(copyStatus = State.CopyStatus.Copied)
              },
              onShareClick = {
                sharingManager.shareText(
                  text = currentState.address.address,
                  title = "Bitcoin Address",
                  completion = null
                )
              }
            )
        )
      }

      is State.ErrorLoadingAddressUiState ->
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState(currentState.partners)
          },
          content =
            Error(
              title = "We couldn’t create an address",
              subline = "We are looking into this. Please try again later."
            )
        )

      is State.LoadingPartnerRedirect ->
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState(currentState.partners)
          },
          content =
            QrCode(
              address = currentState.address.address,
              qrCodeState = currentState.qrCodeState,
              partners = currentState.partners,
              onPartnerClick = { /* Disable during loading */ },
              copyButtonIcon = currentState.copyStatus.icon(),
              copyButtonLabelText = currentState.copyStatus.labelText(),
              onCopyClick = {
                scope.launch {
                  haptics.vibrate(HapticsEffect.MediumClick)
                }
                clipboard.setItem(item = PlainText(data = currentState.address.address))
                state = currentState.copy(copyStatus = State.CopyStatus.Copied)
              },
              onShareClick = {
                sharingManager.shareText(
                  text = currentState.address.address,
                  title = "Bitcoin Address",
                  completion = null
                )
              },
              loadingPartnerId = currentState.partnerInfo.partnerId.value
            )
        )

      is State.PartnerRedirectError ->
        AddressQrCodeBodyModel(
          onBack = {
            state = State.AddressLoadedUiState(
              address = currentState.address,
              addressInvoice = currentState.addressInvoice,
              qrCodeState = currentState.qrCodeState,
              chunkedAddress = currentState.chunkedAddress,
              copyStatus = currentState.copyStatus,
              partners = currentState.partners
            )
          },
          onRefreshClick = null,
          content =
            Error(
              title = "Couldn't open ${currentState.partnerInfo.name}",
              subline = "Please try again later."
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
    open val partners: ImmutableList<PartnerInfo> = emptyImmutableList()

    /**
     * Indicates that we are currently generating a new address, and re-rendering a QR code.
     */
    data class LoadingAddressUiState(
      override val partners: ImmutableList<PartnerInfo>,
    ) : State()

    /**
     * Indicates that we have generated an appropriate receiving address and are rendering it in a
     * QR code, but we are now loading available partners to display.
     *
     * @property [address] - receiving address that will be encoded in a QR code.
     */
    data class LoadingPartnersUiState(
      override val address: BitcoinAddress,
      override val addressInvoice: String,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = address.chunkedAddress(),
      override val copyStatus: CopyStatus,
      override val partners: ImmutableList<PartnerInfo>,
    ) : State()

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
      override val partners: ImmutableList<PartnerInfo>,
    ) : State()

    data object ErrorLoadingAddressUiState : State()

    data class LoadingPartnerRedirect(
      override val address: BitcoinAddress,
      override val addressInvoice: String,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = address.chunkedAddress(),
      override val copyStatus: CopyStatus,
      override val partners: ImmutableList<PartnerInfo>,
      val partnerInfo: PartnerInfo,
    ) : State()

    data class PartnerRedirectError(
      override val address: BitcoinAddress,
      override val addressInvoice: String,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = address.chunkedAddress(),
      override val copyStatus: CopyStatus,
      override val partners: ImmutableList<PartnerInfo>,
      val partnerInfo: PartnerInfo,
      val error: Throwable,
    ) : State()

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
