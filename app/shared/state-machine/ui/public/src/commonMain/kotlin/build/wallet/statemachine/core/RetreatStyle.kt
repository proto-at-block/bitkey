package build.wallet.statemachine.core

import build.wallet.ui.model.Click
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel

/**
 * Describes the style of the top-left toolbar icon button used on
 * screens to retreat.
 */
enum class RetreatStyle {
  Back,
  Close,
}

data class Retreat(
  val style: RetreatStyle,
  val onRetreat: () -> Unit,
) {
  val leadingToolbarAccessory =
    ToolbarAccessoryModel.IconAccessory(
      model =
        IconButtonModel(
          iconModel =
            IconModel(
              when (style) {
                RetreatStyle.Back -> Icon.SmallIconArrowLeft
                RetreatStyle.Close -> Icon.SmallIconX
              },
              iconSize = IconSize.Accessory,
              iconBackgroundType = IconBackgroundType.Circle(circleSize = IconSize.Regular)
            ),
          onClick = Click.standardClick { onRetreat() }
        )
    )
}
