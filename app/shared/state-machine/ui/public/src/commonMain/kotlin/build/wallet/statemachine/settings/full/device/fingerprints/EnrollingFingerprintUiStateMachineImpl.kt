package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintEnrollmentStatus.COMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.INCOMPLETE
import build.wallet.firmware.FingerprintEnrollmentStatus.NOT_IN_PROGRESS
import build.wallet.firmware.FingerprintEnrollmentStatus.UNSPECIFIED
import build.wallet.statemachine.account.create.full.hardware.HardwareFingerprintEnrollmentScreenModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiState.ConfirmingEnrollmentStatusUiState
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiState.ShowingFingerprintInstructionsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiState.StartingEnrollmentUiState

class EnrollingFingerprintUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
) : EnrollingFingerprintUiStateMachine {
  @Composable
  override fun model(props: EnrollingFingerprintProps): ScreenModel {
    var uiState: EnrollingFingerprintUiState by remember {
      mutableStateOf(StartingEnrollmentUiState)
    }

    return when (val state = uiState) {
      StartingEnrollmentUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.startFingerprintEnrollment(
                session = session,
                fingerprintHandle = props.fingerprintHandle
              )
            },
            onSuccess = { uiState = ShowingFingerprintInstructionsUiState() },
            onCancel = props.onCancel,
            isHardwareFake = props.account.config.isHardwareFake,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            eventTrackerContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT
          )
        )
      is ShowingFingerprintInstructionsUiState ->
        HardwareFingerprintEnrollmentScreenModel(
          showingIncompleteEnrollmentError = state.showingIncompleteEnrollmentError,
          incompleteEnrollmentErrorOnPrimaryButtonClick = {
            uiState = state.copy(showingIncompleteEnrollmentError = false)
          },
          onSaveFingerprint = {
            uiState = ConfirmingEnrollmentStatusUiState
          },
          onErrorOverlayClosed = {
            uiState = state.copy(showingIncompleteEnrollmentError = false)
          },
          onBack = props.onCancel,
          eventTrackerScreenIdContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT,
          isNavigatingBack = state.isNavigatingBack,
          presentationStyle = ScreenPresentationStyle.RootFullScreen,
          headline = "Set up another fingerprint"
        )
      ConfirmingEnrollmentStatusUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->
            val enrollmentStatus = commands.getFingerprintEnrollmentStatus(session)
            when (enrollmentStatus) {
              COMPLETE -> EnrollmentStatusResult.Complete(
                // Only retrieve enrolled fingerprints if necessary
                enrolledFingerprints = commands.getEnrolledFingerprints(session)
              )
              INCOMPLETE -> EnrollmentStatusResult.Incomplete
              NOT_IN_PROGRESS -> EnrollmentStatusResult.NotInProgress
              UNSPECIFIED -> EnrollmentStatusResult.Unspecified
            }
          },
          onSuccess = { response ->
            when (response) {
              is EnrollmentStatusResult.Complete -> props.onSuccess(response.enrolledFingerprints)
              EnrollmentStatusResult.Incomplete ->
                uiState =
                  ShowingFingerprintInstructionsUiState(
                    showingIncompleteEnrollmentError = true,
                    isNavigatingBack = true
                  )

              EnrollmentStatusResult.NotInProgress -> {
                // TODO(W-8026): Identify whether the fingerprint was actually enrolled or not,
                // and start a new enrollment if not.
                uiState = StartingEnrollmentUiState
              }
              EnrollmentStatusResult.Unspecified -> error("Unexpected fingerprint enrollment state")
            }
          },
          onCancel = { uiState = ShowingFingerprintInstructionsUiState(isNavigatingBack = true) },
          isHardwareFake = props.account.config.isHardwareFake,
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.CHECKING_FINGERPRINT_ENROLLMENT_STATUS
        )
      )
    }
  }
}

private sealed interface EnrollingFingerprintUiState {
  data class ShowingFingerprintInstructionsUiState(
    val showingIncompleteEnrollmentError: Boolean = false,
    val isNavigatingBack: Boolean = false,
  ) : EnrollingFingerprintUiState

  data object StartingEnrollmentUiState : EnrollingFingerprintUiState

  data object ConfirmingEnrollmentStatusUiState : EnrollingFingerprintUiState
}

internal sealed interface EnrollmentStatusResult {
  data object Unspecified : EnrollmentStatusResult

  data object NotInProgress : EnrollmentStatusResult

  data object Incomplete : EnrollmentStatusResult

  data class Complete(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : EnrollmentStatusResult
}
