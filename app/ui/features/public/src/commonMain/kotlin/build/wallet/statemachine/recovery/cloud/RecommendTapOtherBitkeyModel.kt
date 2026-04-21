package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.RECOMMEND_TAP_OTHER_BITKEY
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Shown when CSEK unseal fails with a W3 device and the backup's recovery auth
 * public key no longer matches the server — indicating the user left a W3
 * upgrade mid-flow. Asks the user to tap their other (W1) Bitkey instead of
 * proceeding into Lost App & Cloud recovery, which cannot currently succeed
 * in this state.
 */
data class RecommendTapOtherBitkeyModel(
  override val onBack: () -> Unit,
  val onTapOtherBitkey: () -> Unit,
) : FormBodyModel(
    id = RECOMMEND_TAP_OTHER_BITKEY,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Use your other Bitkey",
      subline = "The Bitkey you tapped can’t decrypt your cloud backup. " +
        "If you have another Bitkey device, tap it to continue restoring your wallet."
    ),
    primaryButton = ButtonModel(
      text = "Try a different Bitkey",
      size = Footer,
      onClick = StandardClick(onTapOtherBitkey)
    )
  )
