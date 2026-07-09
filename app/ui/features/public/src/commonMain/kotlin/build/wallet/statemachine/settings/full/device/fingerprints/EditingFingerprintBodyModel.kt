package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization

/** Hardware supports a label length of up to 32 characters. */
private const val MAX_LABEL_LENGTH = 32

data class EditingFingerprintBodyModel(
  val index: Int,
  val label: String,
  val textFieldValue: String,
  val onDelete: () -> Unit,
  val onSave: () -> Unit,
  val onValueChange: (String) -> Unit,
  val onBackPressed: () -> Unit,
  val isExistingFingerprint: Boolean,
  val attemptToDeleteLastFingerprint: Boolean,
) : FormBodyModel(
    id = ManagingFingerprintsEventTrackerScreenId.EDIT_FINGERPRINT,
    onBack = onBackPressed,
    toolbar = null,
    header = FormHeaderModel(
      headline = when {
        !isExistingFingerprint -> "Add fingerprint name"
        label.isNotBlank() -> "Manage $label"
        else -> "Manage ${FingerprintHandle.defaultLabel(index)}"
      },
      subline = "Give your fingerprint a title to help distinguish between multiple fingerprints."
    ),
    mainContentList = immutableListOfNotNull(
      FormMainContentModel.TextInput(
        fieldModel = TextFieldModel(
          value = textFieldValue,
          placeholderText = "Fingerprint name",
          testTag = "editing-fingerprint-name-input",
          onValueChange = { newValue, _ -> onValueChange(newValue) },
          keyboardType = TextFieldModel.KeyboardType.Default,
          onDone = onSave,
          capitalization = Capitalization.Sentences,
          maxLength = MAX_LABEL_LENGTH
        )
      ),
      FormMainContentModel.Callout(
        item = CalloutModel(
          title = "At least one fingerprint is required",
          subtitle = StringModel("Add another fingerprint to delete"),
          leadingIcon = Icon.Information,
          treatment = CalloutModel.Treatment.Information
        )
      ).takeIf { attemptToDeleteLastFingerprint }
    ),
    primaryButton = saveFingerprintButton(
      isExistingFingerprint = isExistingFingerprint,
      isEnabled = !isExistingFingerprint || label != textFieldValue,
      onSave = onSave
    ),
    secondaryButton = deleteFingerprintButton(onDelete).takeIf {
      // Only show the delete button if this is an existing fingerprint and not a new enrollment
      isExistingFingerprint
    },
    renderContext = RenderContext.Sheet
  ) {

  override fun automateNextPrimaryScreen() {
    // The primary path for this sheet is saving or starting enrollment, even though the
    // legacy footer layout keeps delete in the raw primary slot.
    if (!isExistingFingerprint || label != textFieldValue) {
      onSave()
    }
  }
}

private fun saveFingerprintButton(
  isExistingFingerprint: Boolean,
  isEnabled: Boolean,
  onSave: () -> Unit,
) = BitkeyInteractionButtonModel(
  text = if (isExistingFingerprint) "Save fingerprint" else "Start fingerprint",
  isEnabled = isEnabled,
  onClick = StandardClick(onSave),
  size = ButtonModel.Size.Footer
)

private fun deleteFingerprintButton(onDelete: () -> Unit) =
  ButtonModel(
    text = "Delete fingerprint",
    treatment = ButtonModel.Treatment.Secondary,
    size = ButtonModel.Size.Footer,
    onClick = StandardClick(onDelete)
  )
