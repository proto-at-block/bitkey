package build.wallet.statemachine.recovery.hardware.initiating

import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.compose.collections.immutableListOf
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class HardwareReplacementInstructionsModel(
  val onContinue: () -> Unit,
  val onClose: () -> Unit,
) : FormBodyModel(
    onBack = onClose,
    toolbar = ToolbarModel(leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onClose)),
    header = FormHeaderModel(headline = "Recover your wallet to a new Bitkey device"),
    mainContentList = immutableListOf(
      FormMainContentModel.Explainer(
        immutableListOf(
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconClock,
            title = "7-day security waiting period",
            body = "During this time, you’ll get regular alerts about the recovery. No action is needed; they are to keep you informed and aware."
          ),
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconMinusStroked,
            title = "Cancel anytime",
            body = "You can cancel this process anytime, and return to using your existing Bitkey device if you find it later."
          ),
          FormMainContentModel.Explainer.Statement(
            leadingIcon = Icon.SmallIconBitcoinStroked,
            title = "Small network fee required",
            body = "To recover your key, you’ll need to initiate an on-chain transaction to complete the process."
          )
        )
      )
    ),
    primaryButton = ButtonModel(
      text = "Continue",
      onClick = StandardClick { onContinue() },
      size = ButtonModel.Size.Footer
    ),
    id = HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INSTRUCTIONS
  )
