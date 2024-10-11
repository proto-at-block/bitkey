package build.wallet.statemachine.settings.full.device.resetdevice.complete

import androidx.compose.runtime.*
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.Showcase.Content.VideoContent.Video.BITKEY_RESET
import build.wallet.statemachine.settings.full.device.resetdevice.ResettingDeviceEventTrackerScreenId
import build.wallet.ui.model.button.ButtonModel

class ResettingDeviceSuccessUiStateMachineImpl : ResettingDeviceSuccessUiStateMachine {
  @Composable
  override fun model(props: ResettingDeviceSuccessProps): ScreenModel {
    return ScreenModel(
      body = ResettingDeviceSuccess(onDone = props.onDone)
    )
  }
}

private data class ResettingDeviceSuccess(
  val onDone: () -> Unit,
) : FormBodyModel(
    id = ResettingDeviceEventTrackerScreenId.RESET_DEVICE_SUCCESS,
    onBack = null,
    onSwipeToDismiss = null,
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.Spacer(),
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.VideoContent(BITKEY_RESET),
        title = "Bitkey device successfully reset",
        body = LabelModel.StringModel("The device has been reset. You can now set it up as a new device, or safely discard, trade in, or give it away.")
      ),
      FormMainContentModel.Spacer()
    ),
    primaryButton = ButtonModel(
      text = "Done",
      requiresBitkeyInteraction = false,
      size = ButtonModel.Size.Footer,
      treatment = ButtonModel.Treatment.Primary,
      onClick = onDone
    ),
    toolbar = null
  )
