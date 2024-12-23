package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.settings.full.device.fingerprints.EditingFingerprintUiState.ConfirmDeleteFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.EditingFingerprintUiState.EditFingerprintHandleUiState

@BitkeyInject(ActivityScope::class)
class EditingFingerprintUiStateMachineImpl : EditingFingerprintUiStateMachine {
  @Composable
  override fun model(props: EditingFingerprintProps): SheetModel {
    var uiState: EditingFingerprintUiState by remember {
      mutableStateOf(EditFingerprintHandleUiState(false))
    }

    return when (val state = uiState) {
      ConfirmDeleteFingerprintUiState -> SheetModel(
        body = ConfirmDeleteFingerprintBodyModel(
          onDelete = { props.onDeleteFingerprint(props.fingerprintToEdit) },
          onCancel = { uiState = EditFingerprintHandleUiState(false) }
        ),
        onClosed = props.onBack
      )
      is EditFingerprintHandleUiState -> {
        var labelValue by remember { mutableStateOf(props.fingerprintToEdit.label) }

        SheetModel(
          body = EditingFingerprintBodyModel(
            index = props.fingerprintToEdit.index,
            label = props.originalFingerprintLabel,
            textFieldValue = labelValue,
            onDelete = {
              uiState = if (props.enrolledFingerprints.fingerprintHandles.size > 1) {
                ConfirmDeleteFingerprintUiState
              } else {
                EditFingerprintHandleUiState(true)
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
            isExistingFingerprint = props.isExistingFingerprint,
            attemptToDeleteLastFingerprint = state.attemptToDeleteLastFingerprint
          ),
          onClosed = props.onBack,
          dragIndicatorVisible = true
        )
      }
    }
  }
}

sealed interface EditingFingerprintUiState {
  /**
   * Presenting the user with the fingerprint label text box and an option to save a new
   * label or delete the fingerprint altogether.
   * @param attemptToDeleteLastFingerprint True if the user is trying to delete the last fingerprint
   */
  data class EditFingerprintHandleUiState(
    val attemptToDeleteLastFingerprint: Boolean = false,
  ) : EditingFingerprintUiState

  /**
   * Confirming that the user actually wants to delete the selected fingerprint
   */
  data object ConfirmDeleteFingerprintUiState : EditingFingerprintUiState
}
