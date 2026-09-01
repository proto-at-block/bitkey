package build.wallet.statemachine.recovery.cloud

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.RESUME_CLOUD_BACKUP_RESTORATION
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.button.ButtonModel.Size.Footer
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

/**
 * Shown when the customer backs out of the final "provision app auth key" tap of
 * cloud restoration. This is not a failure state — it is only reachable via the
 * NFC session's cancellation path, since genuine [build.wallet.nfc.NfcException]s
 * are surfaced by the NFC state machine's own error screen. The backup has
 * already been unsealed and restored at this point, so this screen simply
 * invites the customer to complete the remaining tap.
 */
data class ResumeCloudBackupRestorationModel(
  override val onBack: () -> Unit,
  val onContinue: () -> Unit,
  val onCancel: () -> Unit,
) : FormBodyModel(
    id = RESUME_CLOUD_BACKUP_RESTORATION,
    onBack = onBack,
    toolbar = ToolbarModel(leadingAccessory = BackAccessory(onClick = onBack)),
    header = FormHeaderModel(
      headline = "Your backup was found",
      subline = "Tap your Bitkey device to restore your wallet."
    ),
    primaryButton = BitkeyInteractionButtonModel(
      text = "Continue",
      onClick = StandardClick(onContinue),
      size = Footer
    ),
    secondaryButton = ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = Footer,
      onClick = StandardClick(onCancel)
    )
  )
