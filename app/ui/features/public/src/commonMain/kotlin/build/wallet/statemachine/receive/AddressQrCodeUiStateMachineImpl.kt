package build.wallet.statemachine.receive

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.ADDRESS_VERIFICATION
import build.wallet.analytics.v1.Action
import build.wallet.bitcoin.address.BitcoinAddressInfo
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
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.partnerships.PartnerEventTrackerScreenIdContext
import build.wallet.statemachine.qr.QrCodeService
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.Error
import build.wallet.statemachine.receive.AddressQrCodeBodyModel.Content.QrCode
import build.wallet.statemachine.root.AddressQrCodeLoadingDuration
import build.wallet.statemachine.root.RestoreCopyAddressStateDelay
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.TimeSource
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
  private val addressQrCodeLoadingDuration: AddressQrCodeLoadingDuration,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
) : AddressQrCodeUiStateMachine {
  @Composable
  override fun model(props: AddressQrCodeUiProps): BodyModel {
    var state: State by remember {
      mutableStateOf(State.LoadingAddressUiState())
    }
    val partners by produceState(emptyImmutableList()) {
      getTransferPartnerListF8eClient.getTransferPartners(
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
          value = transferPartners
        }
        .logFailure { "Error loading transfer partners for receive screen" }
        .onFailure {
          // TODO W-14880 Display some message to user informing them partners load failed
          value = emptyImmutableList()
        }
    }

    val scope = rememberStableCoroutineScope()

    when (val currentState = state) {
      is State.LoadingAddressUiState -> {
        if (currentState.pendingQrCode == null) {
          // Phase 1: Generate address and QR code
          LaunchedEffect("loading-address") {
            val startMark = TimeSource.Monotonic.markNow()

            coroutineBinding {
              val addressInfo = bitcoinAddressService.generateAddressInfo().bind()
              val addressInvoice = bitcoinInvoiceUrlEncoder.encode(
                invoice = BitcoinInvoice(address = addressInfo.address)
              )
              val qrCodeResult = qrCodeService.generateQrCode(addressInvoice)
                .logFailure { "Error generating QR code." }
                .bind()
              addressInfo to qrCodeResult
            }.onSuccess { (addressInfo, qrCodeMatrix) ->
              val qrCodeState = QrCodeState.Success(qrCodeMatrix)
              val operationDuration = startMark.elapsedNow()

              state = State.LoadingAddressUiState(
                addressInfo = addressInfo,
                copyStatus = State.CopyStatus.Ready,
                chunkedAddress = addressInfo.address.chunkedAddress(),
                qrCodeState = qrCodeState,
                pendingQrCode = State.PendingQrCode(
                  addressInfo = addressInfo,
                  operationDuration = operationDuration
                )
              )
            }.onFailure {
              state = State.ErrorLoadingAddressUiState
            }
          }
        } else {
          // Phase 2: Wait for text animation delay
          LaunchedEffect("animate-text") {
            val remainingDelay = (addressQrCodeLoadingDuration.value - currentState.pendingQrCode.operationDuration)
              .coerceAtLeast(Duration.ZERO)
            delay(remainingDelay)
            state = State.AddressLoadedUiState(
              addressInfo = currentState.pendingQrCode.addressInfo,
              copyStatus = currentState.copyStatus,
              chunkedAddress = currentState.chunkedAddress,
              qrCodeState = currentState.qrCodeState
            )
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
                address = currentState.addressInfo.address
              ).bind()

            val localTransaction = partnershipTransactionsService.create(
              id = redirectResult.redirectInfo.partnerTransactionId,
              partnerInfo = currentState.partnerInfo,
              type = PartnershipTransactionType.TRANSFER
            ).bind()

            localTransaction to redirectResult
          }.onFailure { error ->
            state = State.PartnerRedirectError(
              addressInfo = currentState.addressInfo,
              qrCodeState = currentState.qrCodeState,
              chunkedAddress = currentState.chunkedAddress,
              copyStatus = currentState.copyStatus,
              partnerInfo = currentState.partnerInfo,
              error = error
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
              addressInfo = currentState.addressInfo,
              qrCodeState = currentState.qrCodeState,
              chunkedAddress = currentState.chunkedAddress,
              copyStatus = currentState.copyStatus
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
          onRefreshClick = {},
          content =
            QrCode(
              address = currentState.addressInfo?.address?.address,
              partners = partners,
              qrCodeState = currentState.qrCodeState,
              onPartnerClick = { /* Disabled during address loading */ },
              copyButtonIcon = currentState.copyStatus.icon(),
              copyButtonLabelText = currentState.copyStatus.labelText(),
              onCopyClick = {},
              onShareClick = {},
              isRefreshing = true
            )
        )

      is State.AddressLoadedUiState ->
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            // the currentState in the lambda was getting cached somehow and giving incorrect values
            // on execution. We grab the state on every execution to circumvent this
            val currentStateSnapshot = state
            if (currentStateSnapshot is State.AddressLoadedUiState) {
              state = State.LoadingAddressUiState(
                addressInfo = currentStateSnapshot.addressInfo,
                qrCodeState = currentStateSnapshot.qrCodeState,
                chunkedAddress = currentStateSnapshot.chunkedAddress,
                copyStatus = currentStateSnapshot.copyStatus
              )
            }
          },
          content =
            QrCode(
              address = currentState.addressInfo.address.address,
              qrCodeState = currentState.qrCodeState,
              partners = partners,
              onPartnerClick = { partner ->
                state = State.LoadingPartnerRedirect(
                  addressInfo = currentState.addressInfo,
                  qrCodeState = currentState.qrCodeState,
                  chunkedAddress = currentState.chunkedAddress,
                  copyStatus = currentState.copyStatus,
                  partnerInfo = partner
                )
              },
              copyButtonIcon = currentState.copyStatus.icon(),
              copyButtonLabelText = currentState.copyStatus.labelText(),
              onCopyClick = {
                // the currentState in the lambda was getting cached somehow and giving incorrect values
                // on execution. We grab the state on every execution to circumvent this
                val currentStateSnapshot = state
                if (currentStateSnapshot is State.AddressLoadedUiState) {
                  scope.launch {
                    haptics.vibrate(HapticsEffect.MediumClick)
                    clipboard.setItem(item = PlainText(data = currentStateSnapshot.addressInfo.address.address))
                    state = currentStateSnapshot.copy(copyStatus = State.CopyStatus.Copied)
                  }
                }
              },
              onShareClick = {
                sharingManager.shareText(
                  text = currentState.addressInfo.address.address,
                  title = "Bitcoin Address",
                  completion = null
                )
              },
              showVerifyOnDeviceButton = props.account.config.hardwareType == HardwareType.W3,
              onVerifyOnDeviceClick = if (props.account.config.hardwareType == HardwareType.W3) {
                {
                  state = State.VerifyingOnDevice(
                    addressInfo = currentState.addressInfo,
                    qrCodeState = currentState.qrCodeState,
                    chunkedAddress = currentState.chunkedAddress,
                    copyStatus = currentState.copyStatus
                  )
                }
              } else {
                null
              }
            )
        )

      is State.ErrorLoadingAddressUiState ->
        AddressQrCodeBodyModel(
          onBack = props.onBack,
          onRefreshClick = {
            state = State.LoadingAddressUiState()
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
          onRefreshClick = { /* Disable during loading */ },
          content =
            QrCode(
              address = currentState.addressInfo.address.address,
              qrCodeState = currentState.qrCodeState,
              partners = partners,
              onPartnerClick = { /* Disable during loading */ },
              copyButtonIcon = currentState.copyStatus.icon(),
              copyButtonLabelText = currentState.copyStatus.labelText(),
              onCopyClick = { /* Disable during loading */ },
              onShareClick = { /* Disable during loading */ },
              loadingPartnerId = currentState.partnerInfo.partnerId.value
            )
        )

      is State.PartnerRedirectError ->
        AddressQrCodeBodyModel(
          onBack = {
            state = State.AddressLoadedUiState(
              addressInfo = currentState.addressInfo,
              qrCodeState = currentState.qrCodeState,
              chunkedAddress = currentState.chunkedAddress,
              copyStatus = currentState.copyStatus
            )
          },
          onRefreshClick = null,
          content =
            Error(
              title = "Couldn't open ${currentState.partnerInfo.name}",
              subline = "Please try again later."
            )
        )

      is State.VerifyingOnDevice ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              // Call getAddress with the same index used to generate the displayed address
              commands.getAddress(session, currentState.addressInfo.index)
            },
            onSuccess = {
              // Return to address loaded state after NFC completes
              state = State.AddressLoadedUiState(
                addressInfo = currentState.addressInfo,
                qrCodeState = currentState.qrCodeState,
                chunkedAddress = currentState.chunkedAddress,
                copyStatus = currentState.copyStatus
              )
            },
            onCancel = {
              // Return to address loaded state on cancel
              state = State.AddressLoadedUiState(
                addressInfo = currentState.addressInfo,
                qrCodeState = currentState.qrCodeState,
                chunkedAddress = currentState.chunkedAddress,
                copyStatus = currentState.copyStatus
              )
            },
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = ADDRESS_VERIFICATION
          )
        ).body
    }
  }

  private sealed class State {
    open val addressInfo: BitcoinAddressInfo? = null
    open val qrCodeState: QrCodeState = QrCodeState.Loading
    open val chunkedAddress: String? = null
    open val copyStatus: CopyStatus = CopyStatus.Ready

    /**
     * Indicates that we are currently generating a new address, and re-rendering a QR code.
     */
    data class LoadingAddressUiState(
      override val addressInfo: BitcoinAddressInfo? = null,
      override val qrCodeState: QrCodeState = QrCodeState.Loading,
      override val chunkedAddress: String? = null,
      override val copyStatus: CopyStatus = CopyStatus.Ready,
      val pendingQrCode: PendingQrCode? = null,
    ) : State()

    data class PendingQrCode(
      val addressInfo: BitcoinAddressInfo,
      val operationDuration: Duration = Duration.ZERO,
    )

    /**
     * Indicates that we have generated an appropriate receiving address and are rendering it in a
     * QR code.
     *
     * @property [addressInfo] - receiving address info (address and index) that will be encoded in a QR code.
     */
    data class AddressLoadedUiState(
      override val addressInfo: BitcoinAddressInfo,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = addressInfo.address.chunkedAddress(),
      override val copyStatus: CopyStatus,
    ) : State()

    data object ErrorLoadingAddressUiState : State()

    data class LoadingPartnerRedirect(
      override val addressInfo: BitcoinAddressInfo,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = addressInfo.address.chunkedAddress(),
      override val copyStatus: CopyStatus,
      val partnerInfo: PartnerInfo,
    ) : State()

    data class PartnerRedirectError(
      override val addressInfo: BitcoinAddressInfo,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = addressInfo.address.chunkedAddress(),
      override val copyStatus: CopyStatus,
      val partnerInfo: PartnerInfo,
      val error: Throwable,
    ) : State()

    /**
     * Indicates that we are verifying the address on the hardware device via NFC.
     */
    data class VerifyingOnDevice(
      override val addressInfo: BitcoinAddressInfo,
      override val qrCodeState: QrCodeState,
      override val chunkedAddress: String? = addressInfo.address.chunkedAddress(),
      override val copyStatus: CopyStatus,
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
