package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.statemachine.account.create.full.hardware.HardwareFingerprintEnrollmentScreenModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiState.ConfirmingEnrollmentStatusUiState
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiState.ShowingFingerprintInstructionsUiState
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiState.StartingEnrollmentUiState

@BitkeyInject(ActivityScope::class)
class EnrollingFingerprintUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val fingerprintNfcCommands: FingerprintNfcCommands,
) : EnrollingFingerprintUiStateMachine {
  @Composable
  override fun model(props: EnrollingFingerprintProps): ScreenModel {
    val initialState = when (props.context) {
      EnrollmentContext.FingerprintReset -> ShowingFingerprintInstructionsUiState()
      EnrollmentContext.AddingFingerprint -> StartingEnrollmentUiState
    }

    var uiState by remember {
      mutableStateOf(initialState)
    }

    return when (val state = uiState) {
      StartingEnrollmentUiState ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              fingerprintNfcCommands.prepareForFingerprintEnrollment(
                commands = commands,
                session = session,
                enrolledFingerprints = props.enrolledFingerprints,
                fingerprintToEnroll = props.fingerprintHandle
              )
            },
            onSuccess = { uiState = ShowingFingerprintInstructionsUiState() },
            onCancel = props.onCancel,
            screenPresentationStyle = ScreenPresentationStyle.Modal,
            shouldLock = false,
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
          onBack = when (props.context) {
            EnrollmentContext.FingerprintReset -> null
            else -> props.onCancel
          },
          eventTrackerContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT,
          isNavigatingBack = state.isNavigatingBack,
          presentationStyle = ScreenPresentationStyle.RootFullScreen,
          headline = when (props.context) {
            EnrollmentContext.FingerprintReset -> "Set up your fingerprint"
            else -> "Add a new fingerprint"
          },
          instructions = "Place your finger on the sensor until you see a blue light. Lift your" +
            " finger and repeat (15-20 times) adjusting your finger position slightly each time," +
            " until the light turns green. Then save your fingerprint."
        )
      ConfirmingEnrollmentStatusUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->

            val enrollmentStatus = fingerprintNfcCommands.checkEnrollmentStatus(
              commands = commands,
              session = session,
              enrolledFingerprints = props.enrolledFingerprints,
              fingerprintHandle = props.fingerprintHandle
            )

            if (props.context == EnrollmentContext.FingerprintReset &&
              enrollmentStatus is EnrollmentStatusResult.Complete
            ) {
              // If we are resetting fingerprints, we need to delete all other fingerprints
              enrollmentStatus.enrolledFingerprints
                .fingerprintHandles.filter { it.index != props.fingerprintHandle.index }
                .forEach { commands.deleteFingerprint(session, it.index) }

              EnrollmentStatusResult.Complete(
                enrolledFingerprints = EnrolledFingerprints(
                  fingerprintHandles = listOf(props.fingerprintHandle)
                )
              )
            } else {
              enrollmentStatus
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
              EnrollmentStatusResult.Unspecified -> error("Unexpected fingerprint enrollment state")
            }
          },
          onCancel = { uiState = ShowingFingerprintInstructionsUiState(isNavigatingBack = true) },
          screenPresentationStyle = ScreenPresentationStyle.Modal,
          eventTrackerContext = NfcEventTrackerScreenIdContext.CHECKING_FINGERPRINT_ENROLLMENT_STATUS,
          shouldLock = props.context != EnrollmentContext.FingerprintReset
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
