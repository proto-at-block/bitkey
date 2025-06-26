package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import bitkey.ui.framework.Navigator
import bitkey.ui.framework.Screen
import bitkey.ui.framework.Sheet
import bitkey.ui.framework.SheetPresenter
import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

data class ManageFingerprintsOptionsSheet(
  val fingerprintResetEnabled: Boolean,
  val onDismiss: () -> Unit,
  val onEditFingerprints: () -> Unit,
  val onCannotUnlock: () -> Unit,
  override val origin: Screen,
) : Sheet

@BitkeyInject(ActivityScope::class)
class ManageFingerprintsOptionsSheetPresenter : SheetPresenter<ManageFingerprintsOptionsSheet> {
  @Composable
  override fun model(
    navigator: Navigator,
    sheet: ManageFingerprintsOptionsSheet,
  ): SheetModel {
    return ManageFingerprintsOptionsSheetModel(
      fingerprintResetEnabled = sheet.fingerprintResetEnabled,
      onDismiss = sheet.onDismiss,
      onEditFingerprints = sheet.onEditFingerprints,
      onCannotUnlock = sheet.onCannotUnlock
    )
  }
}

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

data class ManageFingerprintsOptionsSheetBodyModel(
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
