package bitkey.ui.statemachine.interstitial

import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class WalletAtRiskInterstitialBodyModel(
  val subline: String,
  val buttonText: String,
  val onButtonClick: () -> Unit,
  val onClose: () -> Unit,
) : FormBodyModel(
    id = null,
    onBack = onClose,
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory(
        model = IconButtonModel(
          iconModel = IconModel(
            icon = Icon.SmallIconX,
            iconSize = IconSize.Accessory,
            iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
          ),
          onClick = StandardClick { onClose() }
        )
      )
    ),
    header = FormHeaderModel(
      icon = Icon.LargeIconWarningFilled,
      headline = "Your wallet is at risk",
      subline = subline
    ),
    primaryButton = ButtonModel(
      text = buttonText,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onButtonClick)
    )
  )
