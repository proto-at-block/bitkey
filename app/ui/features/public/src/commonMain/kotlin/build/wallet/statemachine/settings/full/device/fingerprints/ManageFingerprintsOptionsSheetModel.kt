package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

fun ManageFingerprintsOptionsSheetModel(
  fingerprintResetEnabled: Boolean,
  onDismiss: () -> Unit,
  onEditFingerprints: () -> Unit,
  onCannotUnlock: () -> Unit,
) = ManageFingerprintsOptionsSheetBodyModel(
  fingerprintResetEnabled = fingerprintResetEnabled,
  onDismiss = onDismiss,
  onEditFingerprints = onEditFingerprints,
  onCannotUnlock = onCannotUnlock
).asSheetModalScreen(onClosed = onDismiss)

private data class ManageFingerprintsOptionsSheetBodyModel(
  val fingerprintResetEnabled: Boolean,
  val onDismiss: () -> Unit,
  val onEditFingerprints: () -> Unit,
  val onCannotUnlock: () -> Unit,
) : FormBodyModel(
    id = SettingsEventTrackerScreenId.SETTINGS_MANAGE_FINGERPRINTS_OPTIONS_SHEET,
    onBack = onDismiss,
    toolbar = null,
    header = FormHeaderModel(
      headline = "Manage fingerprints",
      subline = "Add, replace, or delete the fingerprints used to unlock your Bitkey device."
    ),
    primaryButton = ButtonModel.BitkeyInteractionButtonModel(
      text = "Edit fingerprints",
      onClick = StandardClick(onEditFingerprints),
      size = ButtonModel.Size.Footer
    ),
    secondaryButton = ButtonModel(
      text = "I can't unlock my Bitkey",
      onClick = StandardClick(onCannotUnlock),
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer
    ).takeIf { fingerprintResetEnabled },
    renderContext = RenderContext.Sheet
  )
