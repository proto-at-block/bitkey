package build.wallet.statemachine.recovery.inprogress.completing

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_SIGN_ROTATE_KEYS
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.recovery.getEventId
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.ErrorFormBodyModelWithOptionalErrorData
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.AwaitingHardwareProofOfPossessionData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.CreatingSpendingKeysWithF8EData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.FailedToCreateSpendingKeysData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.ExitedPerformingSweepData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.FailedPerformingCloudBackupData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.FailedRegeneratingTcCertificatesData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.PerformingCloudBackupData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.PerformingSweepData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RegeneratingTcCertificatesData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.AwaitingChallengeAndCsekSignedWithHardwareData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.FailedToRotateAuthData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.ReadyToCompleteRecoveryData
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.RotatingAuthKeysWithF8eData
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.inprogress.waiting.CancelRecoveryAlertModel
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine

class CompletingRecoveryUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val fullAccountCloudSignInAndBackupUiStateMachine:
    FullAccountCloudSignInAndBackupUiStateMachine,
  private val sweepUiStateMachine: SweepUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
) : CompletingRecoveryUiStateMachine {
  @Composable
  override fun model(props: CompletingRecoveryUiProps): ScreenModel {
    return when (props.completingRecoveryData) {
      is ReadyToCompleteRecoveryData -> {
        var confirmingCancellation by remember { mutableStateOf(false) }

        when (props.completingRecoveryData.physicalFactor) {
          App ->
            DelayAndNotifyNewKeyReady(
              factorToRecover = props.completingRecoveryData.physicalFactor,
              // TODO(W-3420): render accurate fee
              onStopRecovery = {
                confirmingCancellation = true
              },
              onCompleteRecovery = props.completingRecoveryData.startComplete,
              onExit = props.onExit
            )

          Hardware ->
            DelayAndNotifyNewKeyReady(
              factorToRecover = props.completingRecoveryData.physicalFactor,
              // TODO(W-3420): render accurate fee
              onStopRecovery = {
                confirmingCancellation = true
              },
              onCompleteRecovery = props.completingRecoveryData.startComplete,
              onExit = props.onExit
            )
        }.asScreen(
          presentationStyle = props.presentationStyle,
          alertModel =
            if (confirmingCancellation) {
              CancelRecoveryAlertModel(
                onConfirm = {
                  props.completingRecoveryData.cancel()
                  confirmingCancellation = false
                },
                onDismiss = {
                  confirmingCancellation = false
                }
              )
            } else {
              null
            }
        )
      }

      is FailedToRotateAuthData ->
        ErrorFormBodyModel(
          title = "We were unable to complete your recovery.",
          subline = "Make sure you are connected to the internet and try again.",
          primaryButton =
            ButtonDataModel(
              text = "OK",
              onClick = props.completingRecoveryData.onConfirm
            ),
          errorData = ErrorData(
            segment = when (props.completingRecoveryData.factorToRecover) {
              App -> RecoverySegment.DelayAndNotify.LostApp.Completion
              Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Completion
            },
            actionDescription = "Rotating auth keys with f8e to complete recovery",
            cause = props.completingRecoveryData.cause
          ),
          eventTrackerScreenId = CreateAccountEventTrackerScreenId.NEW_ACCOUNT_CREATION_FAILURE
        ).asScreen(presentationStyle = props.presentationStyle)

      is AwaitingChallengeAndCsekSignedWithHardwareData ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            transaction = props.completingRecoveryData.nfcTransaction,
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = APP_DELAY_NOTIFY_SIGN_ROTATE_KEYS
          )
        )

      is RotatingAuthKeysWithF8eData ->
        LoadingBodyModel(
          message = "Updating your credentials...",
          id =
            props.completingRecoveryData.physicalFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_ROTATING_AUTH_KEYS,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS
            ),
          eventTrackerShouldTrack = false
        ).asScreen(presentationStyle = props.presentationStyle)

      is AwaitingHardwareProofOfPossessionData ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            Request.HwKeyProof(
              onSuccess = props.completingRecoveryData.addHwFactorProofOfPossession
            ),
            fullAccountId = props.completingRecoveryData.fullAccountId,
            fullAccountConfig = props.completingRecoveryData.fullAccountConfig,
            appAuthKey = props.completingRecoveryData.appAuthKey,
            onBack = props.completingRecoveryData.rollback,
            screenPresentationStyle = props.presentationStyle
          )
        )

      is CreatingSpendingKeysWithF8EData ->
        LoadingBodyModel(
          message = "Creating your keys...",
          id =
            props.completingRecoveryData.physicalFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_CREATING_SPENDING_KEYS,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_CREATING_SPENDING_KEYS
            ),
          eventTrackerShouldTrack = false
        ).asScreen(props.presentationStyle)

      is FailedToCreateSpendingKeysData ->
        ErrorFormBodyModel(
          title = "We were unable to complete your recovery.",
          subline = "Make sure you are connected to the internet and try again.",
          primaryButton =
            ButtonDataModel(
              text = "Retry",
              onClick = props.completingRecoveryData.onRetry
            ),
          errorData = ErrorData(
            segment = when (props.completingRecoveryData.physicalFactor) {
              App -> RecoverySegment.DelayAndNotify.LostApp.Completion
              Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Completion
            },
            actionDescription = "Creating new spending keys to complete recovery",
            cause = props.completingRecoveryData.cause
          ),
          eventTrackerScreenId =
            props.completingRecoveryData.physicalFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_CREATING_SPENDING_KEYS_ERROR,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_CREATING_SPENDING_KEYS_ERROR
            ),
          eventTrackerShouldTrack = false
        ).asScreen(props.presentationStyle)

      RegeneratingTcCertificatesData ->
        LoadingBodyModel(id = null).asScreen(presentationStyle = props.presentationStyle)

      is FailedRegeneratingTcCertificatesData ->
        ErrorFormBodyModel(
          title = "We were unable to complete your recovery.",
          subline = "Make sure you are connected to the internet and try again.",
          primaryButton =
            ButtonDataModel(
              text = "Retry",
              onClick = props.completingRecoveryData.retry
            ),
          eventTrackerScreenId =
            props.completingRecoveryData.physicalFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_CREATING_SPENDING_KEYS_ERROR,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_TRUSTED_CONTACT_SYNC_ERROR
            ),
          errorData = ErrorData(
            segment = RecoverySegment.DelayAndNotify.LostApp.Completion,
            actionDescription = "Fetching trusted contacts to complete recovery",
            cause = props.completingRecoveryData.cause
          )
        ).asScreen(props.presentationStyle)

      is PerformingCloudBackupData -> {
        fullAccountCloudSignInAndBackupUiStateMachine.model(
          FullAccountCloudSignInAndBackupProps(
            sealedCsek = props.completingRecoveryData.sealedCsek,
            keybox = props.completingRecoveryData.keybox,
            onBackupSaved = props.completingRecoveryData.onBackupFinished,
            onBackupFailed = props.completingRecoveryData.onBackupFailed,
            presentationStyle = props.presentationStyle,
            requireAuthRefreshForCloudBackup = false
          )
        )
      }

      is PerformingSweepData ->
        sweepUiStateMachine.model(
          SweepUiProps(
            sweepData = props.completingRecoveryData.sweepData,
            presentationStyle = props.presentationStyle,
            onExit = props.completingRecoveryData.rollback
          )
        )

      is ExitedPerformingSweepData ->
        ErrorFormBodyModel(
          title = "We have not yet transferred funds from old accounts",
          subline = "Please try again.",
          primaryButton =
            ButtonDataModel(
              text = "Retry",
              onClick = props.completingRecoveryData.retry
            ),
          eventTrackerScreenId =
            props.completingRecoveryData.physicalFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_SWEEP_EXITED,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_SWEEP_EXITED
            ),
          eventTrackerShouldTrack = false,
          errorData = when (props.completingRecoveryData.physicalFactor) {
            App ->
              ErrorData(
                segment = RecoverySegment.DelayAndNotify.LostApp.Sweep,
                actionDescription = "Failed sweeping funds to complete recovery for lost app",
                cause = Error("Failed sweeping funds to complete recovery for lost app")
              )
            Hardware ->
              ErrorData(
                segment = RecoverySegment.DelayAndNotify.LostHardware.Sweep,
                actionDescription = "Failed sweeping funds to complete recovery for lost hardware",
                cause = Error("Failed sweeping funds to complete recovery for lost hardware")
              )
          }
        ).asScreen(props.presentationStyle)

      is FailedPerformingCloudBackupData ->
        ErrorFormBodyModelWithOptionalErrorData(
          title = "We were unable to upload backup",
          subline = "Please try again.",
          errorData = props.completingRecoveryData.cause?.let { cause ->
            ErrorData(
              cause = cause,
              actionDescription = "Uploading backup after recovery",
              segment = RecoverySegment.CloudBackup.FullAccount.Upload
            )
          },
          primaryButton =
            ButtonDataModel(
              text = "Retry",
              onClick = props.completingRecoveryData.retry
            ),
          eventTrackerScreenId =
            props.completingRecoveryData.physicalFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_BACKUP_UPLOAD_FAILURE,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_BACKUP_UPLOAD_FAILURE
            ),
          eventTrackerShouldTrack = false
        ).asScreen(props.presentationStyle)
    }
  }
}
