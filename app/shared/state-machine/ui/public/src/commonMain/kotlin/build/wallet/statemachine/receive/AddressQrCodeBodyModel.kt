package build.wallet.statemachine.receive

import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.qr.QrCodeModel
import build.wallet.ui.model.Click
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconImage
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
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
  sealed interface Content {
    data class QrCode(
      @Redacted val address: String?,
      @Redacted val addressQrCode: QrCodeModel?,
      val copyButtonModel: ButtonModel,
      val shareButtonModel: ButtonModel,
    ) : Content {
      constructor(
        address: String?,
        addressQrCode: QrCodeModel?,
        copyButtonIcon: Icon,
        copyButtonLabelText: String,
        onCopyClick: () -> Unit,
        onShareClick: () -> Unit,
      ) : this(
        address = address,
        addressQrCode = addressQrCode,
        copyButtonModel =
          ButtonModel(
            text = copyButtonLabelText,
            leadingIcon = copyButtonIcon,
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Footer,
            onClick = Click.StandardClick(onCopyClick)
          ),
        shareButtonModel =
          ButtonModel(
            text = "Share",
            leadingIcon = Icon.SmallIconShare,
            treatment = ButtonModel.Treatment.Secondary,
            size = ButtonModel.Size.Footer,
            onClick = Click.StandardClick(onShareClick)
          )
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
                onClick = Click.StandardClick { onRefreshClick() }
              )
          )
      ),
    content = content
  )
}
