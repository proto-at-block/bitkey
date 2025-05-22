package build.wallet.statemachine.recovery.conflict.model

import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.recovery.getEventId
import build.wallet.statemachine.core.Icon.LargeIconWarningFilled
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Treatment.SecondaryDestructive

data class ShowingSomeoneElseIsRecoveringBodyModel(
  val cancelingRecoveryLostFactor: PhysicalFactor,
  val isLoading: Boolean,
  val onCancelRecovery: () -> Unit,
) : FormBodyModel(
    id = cancelingRecoveryLostFactor.getEventId(
      LOST_APP_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT,
      LOST_HW_DELAY_NOTIFY_INITIATION_CANCEL_OTHER_RECOVERY_PROMPT
    ),
    toolbar = null,
    onBack = null,
    header = FormHeaderModel(
      icon = LargeIconWarningFilled,
      headline = "Recovery Conflict",
      sublineModel = when (cancelingRecoveryLostFactor) {
        Hardware -> LabelModel.StringWithStyledSubstringModel.from(
          string = LOST_HW_SUBLINE,
          boldedSubstrings = listOf(LOST_HW_SUBLINE_BOLD)
        )
        App -> LabelModel.StringWithStyledSubstringModel.from(
          string = LOST_APP_SUBLINE,
          boldedSubstrings = listOf(LOST_APP_SUBLINE_BOLD)
        )
      }
    ),
    primaryButton = null,
    secondaryButton = ButtonModel(
      text = "Cancel conflicting recovery",
      treatment = SecondaryDestructive,
      onClick = StandardClick(onCancelRecovery),
      size = ButtonModel.Size.Footer,
      isLoading = isLoading
    )
  )

private const val LOST_HW_SUBLINE_BOLD =
  "If you didn’t initiate this recovery, please tap “Cancel conflicting recovery” before proceeding with your App Key recovery on this phone."
private const val LOST_HW_SUBLINE = """
We’ve detected an attempt to replace your Bitkey hardware device using the mobile phone currently active with your wallet.
  
If you didn’t initiate this recovery, please tap “Cancel conflicting recovery” before proceeding with your App Key recovery on this phone.

If you are attempting to replace your currently paired Bitkey hardware device, please proceed with the recovery process on your active phone."""

private const val LOST_APP_SUBLINE_BOLD =
  "If you didn’t initiate this recovery, please tap “Cancel conflicting recovery”."
private const val LOST_APP_SUBLINE = """
We’ve detected an attempt to recover your wallet to another phone using your paired Bitkey device.
  
If you didn’t initiate this recovery, please tap “Cancel conflicting recovery”.
  
If you are attempting to recover with another phone, please wait and complete your recovery on your new phone.
"""
