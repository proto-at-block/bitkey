package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.compose.collections.immutableListOf
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormHeaderModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.form.RenderContext
import build.wallet.statemachine.settings.full.device.fingerprints.EditingFingerprintUiState.ConfirmDeleteFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.EditingFingerprintUiState.EditFingerprintHandleUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsEventTrackerScreenId.CONFIRM_DELETE_FINGERPRINT
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsEventTrackerScreenId.EDIT_FINGERPRINT
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.BackAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel

class EditingFingerprintUiStateMachineImpl : EditingFingerprintUiStateMachine {
  @Composable
  override fun model(props: EditingFingerprintProps): SheetModel {
    var uiState: EditingFingerprintUiState by remember {
      mutableStateOf(EditFingerprintHandleUiState)
    }

    return when (uiState) {
      ConfirmDeleteFingerprintUiState -> SheetModel(
        body = confirmDeleteFingerprintModel(
          onDelete = { props.onDeleteFingerprint(props.fingerprintToEdit) },
          onCancel = { uiState = EditFingerprintHandleUiState }
        ),
        onClosed = props.onBack
      )
      EditFingerprintHandleUiState -> {
        var labelValue by remember { mutableStateOf(props.fingerprintToEdit.label) }
        SheetModel(
          body = editingFingerprintModel(
            index = props.fingerprintToEdit.index,
            label = labelValue,
            onDelete = {
              uiState = if (props.enrolledFingerprints.fingerprintHandles.size > 1) {
                ConfirmDeleteFingerprintUiState
              } else {
                TODO("W-6597 handle attempt to delete the last remaining fingerprint")
              }
            },
            onSave = {
              props.onSave(
                FingerprintHandle(
                  index = props.fingerprintToEdit.index,
                  label = labelValue
                )
              )
            },
            onValueChange = { labelValue = it },
            onBackPressed = props.onBack,
            isExistingFingerprint = props.isExistingFingerprint
          ),
          onClosed = props.onBack,
          dragIndicatorVisible = true
        )
      }
    }
  }
}

fun editingFingerprintModel(
  index: Int,
  label: String,
  onDelete: () -> Unit,
  onSave: () -> Unit,
  onValueChange: (String) -> Unit,
  onBackPressed: () -> Unit,
  isExistingFingerprint: Boolean,
) = FormBodyModel(
  id = EDIT_FINGERPRINT,
  onBack = onBackPressed,
  toolbar = ToolbarModel(leadingAccessory = CloseAccessory(onBackPressed)),
  header = FormHeaderModel(
    headline = "Manage Fingerprint ${index + 1}",
    subline = "Give your fingerprint a title to help distinguish between multiple fingerprints."
  ),
  mainContentList = immutableListOf(
    FormMainContentModel.TextInput(
      fieldModel = TextFieldModel(
        value = label,
        placeholderText = "Fingerprint name",
        onValueChange = { newValue, _ -> onValueChange(newValue) },
        keyboardType = TextFieldModel.KeyboardType.Default,
        onDone = onSave
      )
    )
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
  secondaryButton = ButtonModel(
    text = if (isExistingFingerprint) "Save fingerprint" else "Start fingerprint",
    requiresBitkeyInteraction = true,
    onClick = onSave,
    size = ButtonModel.Size.Footer,
    treatment = ButtonModel.Treatment.Primary
  ),
  renderContext = RenderContext.Sheet
)

private fun confirmDeleteFingerprintModel(
  onDelete: () -> Unit,
  onCancel: () -> Unit,
) = FormBodyModel(
  id = CONFIRM_DELETE_FINGERPRINT,
  onBack = onCancel,
  toolbar = ToolbarModel(leadingAccessory = BackAccessory(onCancel)),
  header =
    FormHeaderModel(
      headline = "Are you sure you want to delete your fingerprint?",
      subline = "You can’t undo this action. You must have at least one fingerprint saved to device."
    ),
  // TODO (W-7901): Show a read-only version of the fingerprint label here.
  primaryButton =
    ButtonModel(
      text = "Delete fingerprint",
      treatment = ButtonModel.Treatment.SecondaryDestructive,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onDelete)
    ),
  secondaryButton =
    ButtonModel(
      text = "Cancel",
      treatment = ButtonModel.Treatment.Secondary,
      size = ButtonModel.Size.Footer,
      onClick = StandardClick(onCancel)
    ),
  renderContext = RenderContext.Sheet
)

sealed interface EditingFingerprintUiState {
  /**
   * Presenting the user with the fingerprint label text box and an option to save a new
   * label or delete the fingerprint altogether.
   */
  data object EditFingerprintHandleUiState : EditingFingerprintUiState

  /**
   * Confirming that the user actually wants delete the selected fingerprint
   */
  data object ConfirmDeleteFingerprintUiState : EditingFingerprintUiState
}
