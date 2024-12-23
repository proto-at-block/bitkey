package build.wallet.statemachine.settings.full.device.wipedevice.complete

import androidx.compose.runtime.Composable
import build.wallet.compose.collections.immutableListOf
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.FormMainContentModel.Showcase.Content.VideoContent.Video.BITKEY_WIPE
import build.wallet.statemachine.settings.full.device.wipedevice.WipingDeviceEventTrackerScreenId
import build.wallet.ui.model.button.ButtonModel

@BitkeyInject(ActivityScope::class)
class WipingDeviceSuccessUiStateMachineImpl : WipingDeviceSuccessUiStateMachine {
  @Composable
  override fun model(props: WipingDeviceSuccessProps): ScreenModel {
    return ScreenModel(
      body = WipingDeviceSuccess(onDone = props.onDone)
    )
  }
}

private data class WipingDeviceSuccess(
  val onDone: () -> Unit,
) : FormBodyModel(
    id = WipingDeviceEventTrackerScreenId.RESET_DEVICE_SUCCESS,
    onBack = null,
    onSwipeToDismiss = null,
    header = null,
    mainContentList = immutableListOf(
      FormMainContentModel.Spacer(),
      FormMainContentModel.Showcase(
        content = FormMainContentModel.Showcase.Content.VideoContent(BITKEY_WIPE),
        title = "Your Bitkey device is now wiped",
        body = LabelModel.StringModel("Your device has been wiped and can now be safely discarded or passed on.")
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
