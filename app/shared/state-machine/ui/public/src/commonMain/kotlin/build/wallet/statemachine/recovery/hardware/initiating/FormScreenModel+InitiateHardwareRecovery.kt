package build.wallet.statemachine.recovery.hardware.initiating

import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.icon.IconButtonModel
import build.wallet.ui.model.icon.IconModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

fun NewDeviceReadyQuestionModel(
  showingNoDeviceAlert: Boolean,
  onNoDeviceAlertDismiss: () -> Unit,
  primaryAction: ButtonModel?,
  secondaryAction: ButtonModel? = null,
  showBack: Boolean,
  backIconModel: IconModel,
  onBack: () -> Unit,
  presentationStyle: ScreenPresentationStyle,
) = ScreenModel(
  body = NewDeviceReadyQuestionBodyModel(
    primaryAction = primaryAction,
    secondaryAction = secondaryAction,
    showBack = showBack,
    backIconModel = backIconModel,
    onBack = onBack
  ),
  presentationStyle = presentationStyle,
  alertModel =
    if (showingNoDeviceAlert) {
      ButtonAlertModel(
        onDismiss = onNoDeviceAlertDismiss,
        title = "A new Bitkey hardware device is required to replace one that is lost",
        subline = "Visit https://bitkey.world to purchase a new Bitkey hardware device.",
        primaryButtonText = "Got it",
        onPrimaryButtonClick = onNoDeviceAlertDismiss
      )
    } else {
      null
    }
)

data class NewDeviceReadyQuestionBodyModel(
  val primaryAction: ButtonModel?,
  val secondaryAction: ButtonModel? = null,
  val showBack: Boolean,
  val backIconModel: IconModel,
  override val onBack: () -> Unit,
) : FormBodyModel(
    onBack = onBack,
    toolbar =
      ToolbarModel(
        leadingAccessory =
          IconAccessory(
            model =
              IconButtonModel(
                iconModel = backIconModel,
                onClick = StandardClick { onBack() }
              )
          ).takeIf { showBack }
      ),
    header =
      FormHeaderModel(
        headline = "Do you have a new Bitkey hardware device ready to go?",
        subline = "You’ll need to pair a new Bitkey hardware device before you can start the process of replacing the old one."
      ),
    primaryButton = primaryAction,
    secondaryButton = secondaryAction,
    id = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_NEW_DEVICE_READY
  )
