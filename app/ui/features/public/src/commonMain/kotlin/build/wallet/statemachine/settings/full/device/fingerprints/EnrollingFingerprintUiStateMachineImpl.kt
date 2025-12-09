package build.wallet.statemachine.settings.full.device.fingerprints

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintEnrollmentStatus
import build.wallet.firmware.FingerprintHandle
import build.wallet.grants.Grant
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import build.wallet.statemachine.account.create.full.hardware.HardwareFingerprintEnrollmentScreenModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.NotRequired
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.settings.full.device.fingerprints.EnrollingFingerprintUiState.*
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetEnrollmentFailureBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetErrorBodyModel
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetEventTrackerScreenId
import build.wallet.statemachine.settings.full.device.fingerprints.fingerprintreset.FingerprintResetGrantProvisionResult
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel

@BitkeyInject(ActivityScope::class)
class EnrollingFingerprintUiStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val fingerprintNfcCommands: FingerprintNfcCommands,
  private val fingerprintResetGrantNfcHandler: FingerprintResetGrantNfcHandler,
) : EnrollingFingerprintUiStateMachine {
  @Composable
  override fun model(props: EnrollingFingerprintProps): ScreenModel {
    val initialState = when (props.context) {
      is EnrollmentContext.FingerprintReset -> ShowingFingerprintInstructionsUiState()
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
          troubleshootingSheetModel = createTroubleshootingSheetModel(
            context = props.context,
            showingTroubleshootingSheet = state.showingTroubleshootingSheet,
            onContinue = { grant ->
              uiState = EnrollingFingerprintUiState.TroubleshootingNfcSessionUiState(grant = grant)
            },
            onClosed = { uiState = state.copy(showingTroubleshootingSheet = false) }
          ),
          troubleshootingButton = createTroubleshootingButton(
            context = props.context,
            onTrouble = { uiState = state.copy(showingTroubleshootingSheet = true) }
          ),
          onBack = props.onCancel,
          eventTrackerContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT,
          isNavigatingBack = state.isNavigatingBack,
          presentationStyle = ScreenPresentationStyle.RootFullScreen,
          headline = when (props.context) {
            is EnrollmentContext.FingerprintReset -> "Set up your fingerprint"
            EnrollmentContext.AddingFingerprint -> "Add a new fingerprint"
          },
          instructions = "Place your finger on the sensor until you see a blue light. Lift your" +
            " finger and repeat (15-20 times) adjusting your finger position slightly each time," +
            " until the light turns green. Then save your fingerprint."
        )
      ConfirmingEnrollmentStatusUiState -> nfcSessionUIStateMachine.model(
        NfcSessionUIStateMachineProps(
          session = { session, commands ->

            val enrollmentStatus = checkEnrollmentStatus(
              context = props.context,
              session = session,
              commands = commands,
              enrolledFingerprints = props.enrolledFingerprints,
              fingerprintHandle = props.fingerprintHandle
            )

            if (props.context is EnrollmentContext.FingerprintReset &&
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
          shouldLock = props.context !is EnrollmentContext.FingerprintReset,
          hardwareVerification = when (props.context) {
            EnrollmentContext.AddingFingerprint -> Required()
            is EnrollmentContext.FingerprintReset -> NotRequired
          }
        )
      )
      is EnrollingFingerprintUiState.TroubleshootingNfcSessionUiState ->
        nfcSessionUIStateMachine.model(
          fingerprintResetGrantNfcHandler.createGrantProvisionProps(
            grant = state.grant,
            onSuccess = { result ->
              uiState = when (result) {
                is FingerprintResetGrantProvisionResult.ProvideGrantSuccess -> {
                  EnrollingFingerprintUiState.TryThisAgainUiState
                }
                is FingerprintResetGrantProvisionResult.FingerprintResetComplete -> {
                  EnrollingFingerprintUiState.CompletedSuccessfullyUiState(result.enrolledFingerprints)
                }
                is FingerprintResetGrantProvisionResult.ProvideGrantFailed -> {
                  EnrollingFingerprintUiState.ProvideGrantFailedErrorUiState
                }
              }
            },
            onCancel = {
              // If the user cancels, we assume the grant is still valid and can be retried later
              uiState = ShowingFingerprintInstructionsUiState(isNavigatingBack = true)
            },
            onError = { false },
            eventTrackerContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT
          )
        )

      is EnrollingFingerprintUiState.TryThisAgainUiState -> {
        val updateState = {
          uiState = ShowingFingerprintInstructionsUiState(
            isNavigatingBack = true
          )
        }
        return FingerprintResetEnrollmentFailureBodyModel(
          onBackClick = updateState,
          onTryAgain = updateState
        ).asScreen(presentationStyle = ScreenPresentationStyle.Modal)
      }

      is EnrollingFingerprintUiState.ProvideGrantFailedErrorUiState -> {
        val updateState = {
          uiState = ShowingFingerprintInstructionsUiState(
            isNavigatingBack = true
          )
        }
        val errorModel = FingerprintResetErrorBodyModel.GenericError(
          title = "Grant Delivery Failed",
          message = "We couldn't deliver the authorization grant to your hardware. Please try again.",
          cause = RuntimeException("Failed to provide grant during fingerprint reset"),
          eventTrackerScreenId = FingerprintResetEventTrackerScreenId.ERROR_NFC_OPERATION_FAILED
        )
        return errorModel.toFormBodyModel(
          onRetry = updateState,
          onCancel = updateState
        ).asScreen(presentationStyle = ScreenPresentationStyle.Modal)
      }

      is EnrollingFingerprintUiState.CompletedSuccessfullyUiState -> {
        LaunchedEffect("complete-fingerprint-enrollment") {
          props.onSuccess(state.enrolledFingerprints)
        }
        return LoadingBodyModel(
          id = FingerprintResetEventTrackerScreenId.LOADING_GRANT,
          title = "Completing fingerprint enrollment..."
        ).asModalScreen()
      }
    }
  }

  private fun createTroubleshootingSheetModel(
    context: EnrollmentContext,
    showingTroubleshootingSheet: Boolean,
    onContinue: (Grant) -> Unit,
    onClosed: () -> Unit,
  ): SheetModel? {
    return if (context is EnrollmentContext.FingerprintReset && showingTroubleshootingSheet) {
      FingerprintTroubleshootingSheetModel(
        onContinue = { onContinue(context.grant) },
        onClosed = onClosed,
        eventTrackerContext = NfcEventTrackerScreenIdContext.ENROLLING_NEW_FINGERPRINT
      )
    } else {
      null
    }
  }

  private fun createTroubleshootingButton(
    context: EnrollmentContext,
    onTrouble: () -> Unit,
  ): ButtonModel? {
    return if (context is EnrollmentContext.FingerprintReset) {
      ButtonModel(
        text = "Having trouble?",
        treatment = ButtonModel.Treatment.TertiaryNoUnderlineWhite,
        onClick = StandardClick(onTrouble),
        size = ButtonModel.Size.Footer,
        testTag = "having-trouble"
      )
    } else {
      null
    }
  }

  private suspend fun checkEnrollmentStatus(
    context: EnrollmentContext,
    session: NfcSession,
    commands: NfcCommands,
    enrolledFingerprints: EnrolledFingerprints,
    fingerprintHandle: FingerprintHandle,
  ): EnrollmentStatusResult {
    return when (context) {
      is EnrollmentContext.FingerprintReset -> {
        val fpEnrollmentResult = commands.getFingerprintEnrollmentStatus(session, true)
        when (fpEnrollmentResult.status) {
          FingerprintEnrollmentStatus.COMPLETE -> EnrollmentStatusResult.Complete(
            enrolledFingerprints = commands.getEnrolledFingerprints(session)
          )
          FingerprintEnrollmentStatus.INCOMPLETE -> EnrollmentStatusResult.Incomplete
          FingerprintEnrollmentStatus.NOT_IN_PROGRESS -> EnrollmentStatusResult.Incomplete
          FingerprintEnrollmentStatus.UNSPECIFIED -> EnrollmentStatusResult.Unspecified
        }
      }
      is EnrollmentContext.AddingFingerprint -> {
        fingerprintNfcCommands.checkEnrollmentStatus(
          commands = commands,
          session = session,
          enrolledFingerprints = enrolledFingerprints,
          fingerprintHandle = fingerprintHandle
        )
      }
    }
  }
}

private sealed interface EnrollingFingerprintUiState {
  data class ShowingFingerprintInstructionsUiState(
    val showingIncompleteEnrollmentError: Boolean = false,
    val showingTroubleshootingSheet: Boolean = false,
    val isNavigatingBack: Boolean = false,
  ) : EnrollingFingerprintUiState

  data object StartingEnrollmentUiState : EnrollingFingerprintUiState

  data object ConfirmingEnrollmentStatusUiState : EnrollingFingerprintUiState

  data class TroubleshootingNfcSessionUiState(
    val grant: Grant,
  ) : EnrollingFingerprintUiState

  data object TryThisAgainUiState : EnrollingFingerprintUiState

  data object ProvideGrantFailedErrorUiState : EnrollingFingerprintUiState

  data class CompletedSuccessfullyUiState(
    val enrolledFingerprints: EnrolledFingerprints,
  ) : EnrollingFingerprintUiState
}
