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
  val trailingAccessory: ToolbarAccessoryModel? = null,
  /**
   * Optional title rendered by the toolbar.
   *
   * Use [ToolbarTitleModel.Inline] to render a centered title between the leading and trailing
   * accessories. Use [ToolbarTitleModel.Large] to render the title as a large block below the
   * toolbar row (with an optional eyebrow), as drawn by the form screen large-title layout.
   */
  val title: ToolbarTitleModel? = null,
)

/**
 * Convenience accessor for the [ToolbarTitleModel.Large] variant of [ToolbarModel.title], or
 * `null` if the title is missing or is an [ToolbarTitleModel.Inline].
 */
val ToolbarModel.largeTitle: ToolbarTitleModel.Large?
  get() = title as? ToolbarTitleModel.Large

/**
 * Convenience accessor for the [ToolbarTitleModel.Inline] variant of [ToolbarModel.title], or
 * `null` if the title is missing or is a [ToolbarTitleModel.Large].
 */
val ToolbarModel.inlineTitle: ToolbarTitleModel.Inline?
  get() = title as? ToolbarTitleModel.Inline

/**
 * Title rendered by the toolbar.
 */
sealed interface ToolbarTitleModel {
  /**
   * Centered between the leading/trailing accessories.
   */
  data class Inline(
    val title: String,
    val subtitle: String? = null,
  ) : ToolbarTitleModel

  /**
   * Renders as a large title block below the toolbar row, with an optional eyebrow.
   */
  data class Large(
    val title: String? = null,
    val eyebrow: String? = null,
  ) : ToolbarTitleModel
}

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
