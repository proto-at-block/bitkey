package build.wallet.statemachine.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.partnerships.PartnerInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.qr.QrCodeState
import build.wallet.ui.app.moneyhome.receive.AddressQrCodeScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

data class AddressQrCodeBodyModel(
  override val onBack: () -> Unit,
  val toolbarModel: ToolbarModel,
  val content: Content,
  // We don't want to track this for privacy reasons
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  sealed interface Content {
    data class QrCode(
      /** The formatted display string for the address */
      @Redacted val addressDisplayString: LabelModel,
      /** When true, the address is still showing a loading placeholder instead of a real value. */
      val isAddressLoadingPlaceholder: Boolean,
      /** QR code state for handling loading, success, and error states */
      @Redacted val qrCodeState: QrCodeState,
      val partners: ImmutableList<PartnerInfo>,
      val onPartnerClick: (PartnerInfo) -> Unit,
      val copyButtonIcon: Icon,
      val copyButtonLabelText: String,
      val onCopyClick: () -> Unit,
      val onShareClick: () -> Unit,
      /** When true, the address section is collapsed by default with a chevron toggle. */
      val hideAddressByDefault: Boolean = false,
      /** When non-null, shows a "Verify" button to verify the address on hardware. */
      val onVerifyClick: (() -> Unit)? = null,
      val loadingPartnerId: String? = null,
      val isRefreshing: Boolean = false,
    ) : Content {
      constructor(
        address: String?,
        qrCodeState: QrCodeState,
        partners: ImmutableList<PartnerInfo> = persistentListOf(),
        onPartnerClick: (PartnerInfo) -> Unit,
        copyButtonIcon: Icon,
        copyButtonLabelText: String,
        onCopyClick: () -> Unit,
        onShareClick: () -> Unit,
        hideAddressByDefault: Boolean = false,
        onVerifyClick: (() -> Unit)? = null,
        loadingPartnerId: String? = null,
        isRefreshing: Boolean = false,
      ) : this(
        addressDisplayString = address?.let { LabelModel.chunkedAddress(it) }
          ?: LabelModel.StringModel("Loading..."),
        isAddressLoadingPlaceholder = address == null,
        qrCodeState = qrCodeState,
        partners = partners,
        onPartnerClick = onPartnerClick,
        copyButtonIcon = copyButtonIcon,
        copyButtonLabelText = copyButtonLabelText,
        onCopyClick = onCopyClick,
        onShareClick = onShareClick,
        hideAddressByDefault = hideAddressByDefault,
        onVerifyClick = onVerifyClick,
        loadingPartnerId = loadingPartnerId,
        isRefreshing = isRefreshing
      )
    }

    data class Error(
      val title: String,
      val subline: String,
    ) : Content
  }

  constructor(
    onBack: () -> Unit,
    onRefreshClick: (() -> Unit)?,
    content: Content,
  ) : this(
    onBack = onBack,
    toolbarModel =
      ToolbarModel(
        leadingAccessory = CloseAccessory(onClick = onBack),
        middleAccessory = ToolbarMiddleAccessoryModel("Receive"),
        trailingAccessory = onRefreshClick?.let {
          ToolbarAccessoryModel.IconAccessory(
            model =
              IconButtonModel(
                iconModel =
                  IconModel(
                    iconImage = IconImage.LocalImage(Icon.SmallIconRefresh),
                    iconSize = IconSize.Accessory,
                    iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
                  ),
                testTag = "receive-address-refresh",
                onClick = StandardClick { onRefreshClick() }
              )
          )
        }
      ),
    content = content
  )

  @Composable
  override fun render(modifier: Modifier) {
    AddressQrCodeScreen(modifier, model = this)
  }
}
