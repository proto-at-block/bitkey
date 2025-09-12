package build.wallet.statemachine.settings.full.device.fingerprints

import build.wallet.compose.collections.immutableListOfNotNull
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.core.form.*
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.button.ButtonModel.Companion.BitkeyInteractionButtonModel
import build.wallet.ui.model.callout.CalloutModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.input.TextFieldModel.Capitalization
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import build.wallet.ui.model.toolbar.ToolbarModel

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
    toolbar = ToolbarModel(
      leadingAccessory = ToolbarAccessoryModel.IconAccessory.CloseAccessory(onBackPressed)
    ),
    header = FormHeaderModel(
      headline = when {
        !isExistingFingerprint -> "Add fingerprint name"
        label.isNotBlank() -> "Manage $label"
        else -> "Manage Finger ${index + 1}"
      },
      subline = "Give your fingerprint a title to help distinguish between multiple fingerprints."
    ),
    mainContentList = immutableListOfNotNull(
      FormMainContentModel.TextInput(
        fieldModel = TextFieldModel(
          value = textFieldValue,
          placeholderText = "Fingerprint name",
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
          leadingIcon = Icon.SmallIconInformationFilled,
          treatment = CalloutModel.Treatment.Information
        )
      ).takeIf { attemptToDeleteLastFingerprint }
    ),
    primaryButton = ButtonModel(
      text = "Delete fingerprint",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onDelete)
    ).takeIf {
      // Only show the delete button if this is an existing fingerprint and not a new enrollment
      isExistingFingerprint
    },
    secondaryButton = BitkeyInteractionButtonModel(
      text = if (isExistingFingerprint) "Save fingerprint" else "Start fingerprint",
      isEnabled = !isExistingFingerprint || label != textFieldValue,
      onClick = StandardClick(onSave),
      size = ButtonModel.Size.Footer
    ),
    renderContext = RenderContext.Sheet
  )
