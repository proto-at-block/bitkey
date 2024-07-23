package build.wallet.statemachine.nfc

import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.IconBackgroundType
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.icon.IconSize
import build.wallet.ui.model.icon.IconTint
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Model for the FWUP instructions screen
 */
data class FwupInstructionsBodyModel(
  override val onBack: () -> Unit,
  val toolbarModel: ToolbarModel,
  val headerModel: FormHeaderModel,
  val buttonModel: ButtonModel,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel() {
  constructor(
    onClose: () -> Unit,
    headerModel: FormHeaderModel,
    buttonText: String,
    onButtonClick: () -> Unit,
    eventTrackerScreenId: EventTrackerScreenId?,
    eventTrackerContext: EventTrackerContext? = null,
  ) : this(
    onBack = onClose,
    toolbarModel =
      ToolbarModel(
        leadingAccessory =
          ToolbarAccessoryModel.IconAccessory(
            model =
              IconButtonModel(
                iconModel =
                  IconModel(
                    icon = Icon.SmallIconX,
                    iconSize = IconSize.Accessory,
                    iconBackgroundType =
                      IconBackgroundType.Circle(
                        circleSize = IconSize.Regular,
                        color = IconBackgroundType.Circle.CircleColor.TranslucentWhite
                      ),
                    iconTint = IconTint.OnTranslucent
                  ),
                onClick = StandardClick(onClose)
              )
          )
      ),
    headerModel = headerModel,
    buttonModel =
      BitkeyInteractionButtonModel(
        text = buttonText,
        size = Footer,
        onClick = StandardClick(onButtonClick)
      ),
    eventTrackerScreenInfo =
      eventTrackerScreenId?.let {
        EventTrackerScreenInfo(
          eventTrackerScreenId = it,
          eventTrackerContext = eventTrackerContext
        )
      }
  )
}
