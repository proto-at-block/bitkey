package build.wallet.ui.model.toolbar

import build.wallet.statemachine.core.Icon.ArrowLeft
import build.wallet.statemachine.core.Icon.Question
import build.wallet.statemachine.core.Icon.X
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconBackgroundType.Circle
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize.Accessory
import build.wallet.ui.model.icon.IconSize.Regular

data class ToolbarModel(
  val leadingAccessory: ToolbarAccessoryModel? = null,
  val middleAccessory: ToolbarMiddleAccessoryModel? = null,
  val trailingAccessory: ToolbarAccessoryModel? = null,
)

data class ToolbarMiddleAccessoryModel(
  val title: String,
  val subtitle: String? = null,
)

sealed interface ToolbarAccessoryModel {
  data class ButtonAccessory(
    val model: ButtonModel,
  ) : ToolbarAccessoryModel

  data class IconAccessory(
    val model: IconButtonModel,
  ) : ToolbarAccessoryModel {
    companion object {
      fun BackAccessory(onClick: () -> Unit) =
        IconAccessory(
          model =
            IconButtonModel(
              iconModel =
                IconModel(
                  ArrowLeft,
                  iconSize = Accessory,
                  iconBackgroundType = Circle(circleSize = Regular)
                ),
              onClick = StandardClick(onClick),
              testTag = "toolbar-back"
            )
        )

      fun CloseAccessory(onClick: () -> Unit) =
        IconAccessory(
          model =
            IconButtonModel(
              iconModel =
                IconModel(
                  X,
                  iconSize = Accessory,
                  iconBackgroundType = Circle(circleSize = Regular)
                ),
              onClick = StandardClick { onClick() },
              testTag = "toolbar-close"
            )
        )

      fun QuestionAccessory(onClick: () -> Unit) =
        IconAccessory(
          model =
            IconButtonModel(
              iconModel =
                IconModel(
                  Question,
                  iconSize = Accessory,
                  iconBackgroundType = Circle(circleSize = Regular)
                ),
              onClick = StandardClick(onClick),
              testTag = "toolbar-question"
            )
        )
    }
  }
}
