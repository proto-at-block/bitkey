package build.wallet.statemachine.recovery.conflict.model

import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.LabelModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.toolbar.ToolbarModel

data class ShowingNoLongerRecoveringBodyModel(
  val canceledRecoveringFactor: PhysicalFactor,
  val isLoading: Boolean,
  override val errorData: ErrorData?,
  val onAcknowledge: () -> Unit,
) : FormBodyModel(
    id = DelayNotifyRecoveryEventTrackerScreenId.RECOVERY_CANCELED_NO_LONGER_RECOVERING,
    onBack = null,
    toolbar = ToolbarModel(),
    header = FormHeaderModel(
      headline = "Your recovery attempt has been canceled.",
      sublineModel = when (canceledRecoveringFactor) {
        Hardware -> LabelModel.StringWithStyledSubstringModel.from(
          string = CANCELED_HW_SUBLINE,
          boldedSubstrings = listOf(CANCELED_HW_SUBLINE_BOLD)
        )
        App -> LabelModel.StringWithStyledSubstringModel.from(
          string = CANCELED_APP_SUBLINE,
          boldedSubstrings = listOf(CANCELED_APP_SUBLINE_BOLD)
        )
      }
    ),
    primaryButton = ButtonModel(
      text = "Got it",
      onClick = StandardClick(onAcknowledge),
      size = ButtonModel.Size.Footer,
      isLoading = isLoading
    ),
    errorData = errorData
  )

private const val CANCELED_HW_SUBLINE_BOLD =
  "If you didn’t cancel this process, immediately go to support.bitkey.build to learn more before attempting your recovery again."
private const val CANCELED_HW_SUBLINE = """
Your paired Bitkey device was used with another phone to cancel your replacement process.
  
If you didn’t cancel this process, immediately go to support.bitkey.build to learn more before attempting your recovery again.
  
Your funds might be at risk.
"""

private const val CANCELED_APP_SUBLINE_BOLD =
  "If you didn’t cancel this process, immediately go to support.bitkey.build to learn more before attempting your recovery again."
private const val CANCELED_APP_SUBLINE = """
Your paired mobile phone was used to cancel your App Key recovery process on this phone.
  
If you didn’t cancel this process, immediately go to support.bitkey.build to learn more before attempting your recovery again.

Your funds might be at risk.
"""
