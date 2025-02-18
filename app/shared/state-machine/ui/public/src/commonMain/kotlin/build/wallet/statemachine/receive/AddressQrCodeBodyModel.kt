package build.wallet.statemachine.receive

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.ui.app.moneyhome.receive.AddressQrCodeScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarMiddleAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel
import dev.zacsweers.redacted.annotations.Redacted

data class AddressQrCodeBodyModel(
  override val onBack: () -> Unit,
  val toolbarModel: ToolbarModel,
  val content: Content,
  // We don't want to track this for privacy reasons
  override val eventTrackerScreenInfo: EventTrackerScreenInfo? = null,
) : BodyModel() {
  lateinit var onCopyClick: () -> Unit

  sealed interface Content {
    data class QrCode(
      /** URL of the remote image to fetch for the QR code */
      @Redacted val addressQrImageUrl: String?,
      /** The formatted display string for the address */
      @Redacted val addressDisplayString: LabelModel,
      /** Fallback QR code data to use to manually draw a QR code image if fetching the remote fails */
      @Redacted val fallbackAddressQrCodeModel: QrCodeModel?,
      val copyButtonModel: ButtonModel,
      val shareButtonModel: ButtonModel,
      val onCopyClick: () -> Unit,
    ) : Content {
      constructor(
        addressQrImageUrl: String?,
        address: String?,
        fallbackAddressQrCodeModel: QrCodeModel?,
        copyButtonIcon: Icon,
        copyButtonLabelText: String,
        onCopyClick: () -> Unit,
        onShareClick: () -> Unit,
      ) : this(
        addressQrImageUrl = addressQrImageUrl,
        // Chunk the address into 4-letter size groups and then color all the odd
        // substrings ON60 (and the even substrings will be colored with primary color)
        addressDisplayString = address?.chunked(4)?.let { addressParts ->
          LabelModel.StringWithStyledSubstringModel.from(
            string = addressParts.joinToString(" "),
            substringToColor = addressParts
              // Filter to only the odd indices to color those substrings
              .filterIndexed { index, _ -> index % 2 != 0 }
              // Map to ON60 color
              .associateWith { LabelModel.Color.ON60 }
          )
        } // Fall back on showing "..." while we are loading an address
          ?: LabelModel.StringModel("..."),
        fallbackAddressQrCodeModel = fallbackAddressQrCodeModel,
        copyButtonModel =
          ButtonModel(
            text = copyButtonLabelText,
            leadingIcon = copyButtonIcon,
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Footer,
            onClick = StandardClick(onCopyClick)
          ),
        shareButtonModel =
          ButtonModel(
            text = "Share",
            leadingIcon = Icon.SmallIconShare,
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Footer,
            onClick = StandardClick(onShareClick)
          ),
        onCopyClick = onCopyClick
      )
    }

    data class Error(
      val title: String,
      val subline: String,
    ) : Content
  }

  constructor(
    onBack: () -> Unit,
    onRefreshClick: () -> Unit,
    content: Content,
  ) : this(
    onBack = onBack,
    toolbarModel =
      ToolbarModel(
        leadingAccessory = CloseAccessory(onClick = onBack),
        middleAccessory = ToolbarMiddleAccessoryModel("Receive"),
        trailingAccessory =
          ToolbarAccessoryModel.IconAccessory(
            model =
              IconButtonModel(
                iconModel =
                  IconModel(
                    iconImage = IconImage.LocalImage(Icon.SmallIconRefresh),
                    iconSize = IconSize.Accessory,
                    iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
                  ),
                onClick = StandardClick { onRefreshClick() }
              )
          )
      ),
    content = content
  )

  @Composable
  override fun render(modifier: Modifier) {
    AddressQrCodeScreen(modifier, model = this)
  }
}
