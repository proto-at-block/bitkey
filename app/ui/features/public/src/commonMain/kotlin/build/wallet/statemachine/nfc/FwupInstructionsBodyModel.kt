package build.wallet.statemachine.nfc

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import bitkey.account.HardwareType
import build.wallet.analytics.events.EventTrackerContext
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.app.nfc.FwupInstructionsScreen
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.icon.*
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
  val hardwareType: HardwareType,
  val onHelpClick: (() -> Unit)? = null,
  override val eventTrackerScreenInfo: EventTrackerScreenInfo?,
) : BodyModel() {
  constructor(
    onClose: () -> Unit,
    headerModel: FormHeaderModel,
    buttonText: String,
    onButtonClick: () -> Unit,
    hardwareType: HardwareType,
    onHelpClick: (() -> Unit)? = null,
    eventTrackerScreenId: EventTrackerScreenId?,
    eventTrackerContext: EventTrackerContext? = null,
  ) : this(
    onBack = onClose,
    toolbarModel = fwupInstructionsToolbarModel(onClose = onClose, onHelpClick = onHelpClick),
    headerModel = headerModel,
    buttonModel =
      BitkeyInteractionButtonModel(
        text = buttonText,
        size = Footer,
        onClick = StandardClick(onButtonClick)
      ),
    hardwareType = hardwareType,
    onHelpClick = onHelpClick,
    eventTrackerScreenInfo =
      eventTrackerScreenId?.let {
        EventTrackerScreenInfo(
          eventTrackerScreenId = it,
          eventTrackerContext = eventTrackerContext
        )
      }
  )

  @Composable
  override fun render(modifier: Modifier) {
    FwupInstructionsScreen(modifier, model = this)
  }
}

private fun fwupInstructionsToolbarModel(
  onClose: () -> Unit,
  onHelpClick: (() -> Unit)?,
) = ToolbarModel(
  leadingAccessory =
    ToolbarAccessoryModel.IconAccessory(
      model =
        IconButtonModel(
          iconModel =
            IconModel(
              icon = Icon.X,
              iconSize = IconSize.Accessory,
              iconBackgroundType =
                IconBackgroundType.Circle(
                  circleSize = IconSize.Regular,
                  color = IconBackgroundType.Circle.CircleColor.TranslucentWhite
                ),
              iconTint = IconTint.OnTranslucent
            ),
          testTag = "fwup-instructions-close",
          onClick = StandardClick(onClose)
        )
    ),
  trailingAccessory =
    onHelpClick?.let {
      ToolbarAccessoryModel.IconAccessory(
        model =
          IconButtonModel(
            iconModel =
              IconModel(
                icon = Icon.Question,
                iconSize = IconSize.Accessory,
                iconBackgroundType =
                  IconBackgroundType.Circle(
                    circleSize = IconSize.Regular,
                    color = IconBackgroundType.Circle.CircleColor.TranslucentWhite
                  ),
                iconTint = IconTint.OnTranslucent
              ),
            testTag = "fwup-instructions-help",
            onClick = StandardClick(it)
          )
      )
    }
)
