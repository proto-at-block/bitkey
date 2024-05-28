package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.firmware.FirmwareFeatureFlag
import build.wallet.home.GettingStartedTask
import build.wallet.home.GettingStartedTaskDao
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.EnrolledFingerprintResult
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.AddingNewFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.CheckingFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.DeletingFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.EditingFingerprintUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.ListingFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.RetrievingEnrolledFingerprintsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.ManagingFingerprintsUiState.SavingFingerprintLabelUiState

class ManagingFingerprintsUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val editingFingerprintUiStateMachine: EditingFingerprintUiStateMachine,
  private val enrollingFingerprintUiStateMachine: EnrollingFingerprintUiStateMachine,
  private val gettingStartedTaskDao: GettingStartedTaskDao,
) : ManagingFingerprintsUiStateMachine {
  @Composable
  override fun model(props: ManagingFingerprintsProps): ScreenModel {
    var uiState: ManagingFingerprintsUiState by remember {
      mutableStateOf(RetrievingEnrolledFingerprintsUiState())
    }

    return when (val state = uiState) {
      is ListingFingerprintsUiState -> ListingFingerprintsBodyModel(
        enrolledFingerprints = state.enrolledFingerprints,
        onBack = props.onBack,
        onEditFingerprint = {
          uiState = EditingFingerprintUiState(
            enrolledFingerprints = state.enrolledFingerprints,
            isExistingFingerprint = true,
            fingerprintToEdit = it
          )
        },
        onAddFingerprint = {
          uiState = EditingFingerprintUiState(
            enrolledFingerprints = state.enrolledFingerprints,
            isExistingFingerprint = false,
            fingerprintToEdit = FingerprintHandle(index = it, label = "")
          )
        }
      ).asRootScreen()
      is EditingFingerprintUiState -> ScreenModel(
        body = ListingFingerprintsBodyModel(
          enrolledFingerprints = state.enrolledFingerprints,
          onBack = props.onBack,
          onEditFingerprint = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = it
            )
          },
          onAddFingerprint = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = false,
              fingerprintToEdit = FingerprintHandle(index = it, label = "")
            )
          }
        ),
        bottomSheetModel = editingFingerprintUiStateMachine.model(
          EditingFingerprintProps(
            enrolledFingerprints = state.enrolledFingerprints,
            onBack = {
              uiState = ListingFingerprintsUiState(
                enrolledFingerprints = state.enrolledFingerprints
              )
            },
            onSave = {
              if (state.isExistingFingerprint) {
                uiState = SavingFingerprintLabelUiState(
                  enrolledFingerprints = state.enrolledFingerprints,
                  fingerprintHandle = it
                )
              } else {
                uiState = AddingNewFingerprintUiState(
                  enrolledFingerprints = state.enrolledFingerprints,
                  fingerprintHandle = it
                )
              }
            },
            onDeleteFingerprint = {
              uiState = DeletingFingerprintUiState(
                enrolledFingerprints = state.enrolledFingerprints,
                fingerprintToDelete = state.fingerprintToEdit
              )
            },
            fingerprintToEdit = state.fingerprintToEdit,
            isExistingFingerprint = state.isExistingFingerprint
          )
        )
      )
      is SavingFingerprintLabelUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            // In the event the user backed out of enrollment and is now trying to save the label
            // for another fingerprint, cancel any ongoing enrollment.
            commands.cancelFingerprintEnrollment(session)
            commands.setFingerprintLabel(
              session,
              FingerprintHandle(
                index = state.fingerprintHandle.index,
                label = state.fingerprintHandle.label
              )
            )
            commands.getEnrolledFingerprints(session)
          },
          onSuccess = { uiState = ListingFingerprintsUiState(enrolledFingerprints = it) },
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = state.fingerprintHandle
            )
          },
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.SAVE_FINGERPRINT_LABEL
        )
      )
      is CheckingFingerprintsUiState -> TODO("W-6590")
      is AddingNewFingerprintUiState -> enrollingFingerprintUiStateMachine.model(
        EnrollingFingerprintProps(
          account = props.account,
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = false,
              fingerprintToEdit = state.fingerprintHandle
            )
          },
          onSuccess = {
            gettingStartedTaskDao.updateTask(
              id = GettingStartedTask.TaskId.AddAdditionalFingerprint,
              state = GettingStartedTask.TaskState.Complete
            )

            uiState = ListingFingerprintsUiState(it)
          },
          fingerprintHandle = state.fingerprintHandle,
          enrolledFingerprints = state.enrolledFingerprints
        )
      )
      is DeletingFingerprintUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            // In the event the user backed out of enrollment and is now trying to delete a
            // different fingerprint, cancel any ongoing enrollment.
            commands.cancelFingerprintEnrollment(session)
            commands.deleteFingerprint(session, state.fingerprintToDelete.index)
            commands.getEnrolledFingerprints(session)
          },
          onSuccess = { uiState = ListingFingerprintsUiState(it) },
          onCancel = {
            uiState = EditingFingerprintUiState(
              enrolledFingerprints = state.enrolledFingerprints,
              isExistingFingerprint = true,
              fingerprintToEdit = state.fingerprintToDelete
            )
          },
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.DELETE_FINGERPRINT
        )
      )
      is RetrievingEnrolledFingerprintsUiState -> {
        if (state.fwUpdateRequired) {
          LaunchedEffect("fwup-required-for-fingerprints") {
            props.onFwUpRequired()
          }
        }
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              // Check that the fw supports multiple fingerprints
              val enabled = commands.getFirmwareFeatureFlags(session)
                .find { it.flag == FirmwareFeatureFlag.MULTIPLE_FINGERPRINTS }
                ?.enabled

              when (enabled) {
                true -> {
                  // In the event the user backed out of enrollment, either through a crash or manually,
                  // cancel any ongoing enrollment.
                  commands.cancelFingerprintEnrollment(session)
                  EnrolledFingerprintResult.Success(commands.getEnrolledFingerprints(session))
                }
                else -> EnrolledFingerprintResult.FwUpRequired
              }
            },
            onSuccess = {
              uiState = when (it) {
                EnrolledFingerprintResult.FwUpRequired -> RetrievingEnrolledFingerprintsUiState(
                  fwUpdateRequired = true
                )
                is EnrolledFingerprintResult.Success -> when (props.entryPoint) {
                  EntryPoint.MONEY_HOME -> EditingFingerprintUiState(
                    enrolledFingerprints = it.enrolledFingerprints,
                    isExistingFingerprint = false,
                    fingerprintToEdit = FingerprintHandle(index = 1, label = "")
                  )
                  EntryPoint.DEVICE_SETTINGS -> ListingFingerprintsUiState(
                    it.enrolledFingerprints
                  )
                }
              }
            },
            onCancel = props.onBack,
            isHardwareFake = props.account.config.isHardwareFake,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.GET_ENROLLED_FINGERPRINTS
          )
        )
      }
    }
  }
}

private sealed interface ManagingFingerprintsUiState {
  data class ListingFingerprintsUiState(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : ManagingFingerprintsUiState

  data class CheckingFingerprintsUiState(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : ManagingFingerprintsUiState

  data class EditingFingerprintUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val isExistingFingerprint: Boolean,
    val fingerprintToEdit: FingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class AddingNewFingerprintUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintHandle: FingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class SavingFingerprintLabelUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintHandle: FingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class DeletingFingerprintUiState(
    val enrolledFingerprints: EnrolledFingerprints,
    val fingerprintToDelete: FingerprintHandle,
  ) : ManagingFingerprintsUiState

  data class RetrievingEnrolledFingerprintsUiState(
    val fwUpdateRequired: Boolean = false,
  ) : ManagingFingerprintsUiState
}
