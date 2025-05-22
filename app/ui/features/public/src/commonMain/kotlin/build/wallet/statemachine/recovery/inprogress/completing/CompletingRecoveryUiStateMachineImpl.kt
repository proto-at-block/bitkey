package build.wallet.statemachine.recovery.inprogress.completing

import androidx.compose.runtime.*
import bitkey.recovery.RecoveryStatusService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_SIGN_ROTATE_KEYS
import build.wallet.analytics.events.screen.id.CreateAccountEventTrackerScreenId
import build.wallet.analytics.events.screen.id.DelayNotifyRecoveryEventTrackerScreenId
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.compose.coroutines.rememberStableCoroutineScope
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.recovery.LocalRecoveryAttemptProgress.SweptFunds
import build.wallet.recovery.getEventId
import build.wallet.recovery.socrec.PostSocRecTaskRepository
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupProps
import build.wallet.statemachine.cloud.FullAccountCloudSignInAndBackupUiStateMachine
import build.wallet.statemachine.core.*
import build.wallet.statemachine.core.LabelModel.StringModel
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.CreatingSpendingKeysData.*
import build.wallet.statemachine.data.recovery.inprogress.RecoveryInProgressData.CompletingRecoveryData.RotatingAuthData.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.inprogress.waiting.cancelRecoveryAlertModel
import build.wallet.statemachine.recovery.sweep.SweepUiProps
import build.wallet.statemachine.recovery.sweep.SweepUiStateMachine
import kotlinx.coroutines.launch

@BitkeyInject(ActivityScope::class)
class CompletingRecoveryUiStateMachineImpl(
  private val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  private val fullAccountCloudSignInAndBackupUiStateMachine:
    FullAccountCloudSignInAndBackupUiStateMachine,
  private val sweepUiStateMachine: SweepUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val postSocRecTaskRepository: PostSocRecTaskRepository,
  private val recoveryStatusService: RecoveryStatusService,
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
              onStopRecovery = if (props.completingRecoveryData.canCancelRecovery) {
                { confirmingCancellation = true }
              } else {
                null
              },
              onCompleteRecovery = props.completingRecoveryData.startComplete,
              onExit = props.onExit
            )
        }.asScreen(
          presentationStyle = props.presentationStyle,
          alertModel =
            if (confirmingCancellation) {
              cancelRecoveryAlertModel(
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
            eventTrackerContext = APP_DELAY_NOTIFY_SIGN_ROTATE_KEYS,
            hardwareVerification = Required(useRecoveryPubKey = true)
          )
        )

      is FetchingSealedDelegatedDecryptionKeyStringData ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            transaction = props.completingRecoveryData.nfcTransaction,
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_UNSEAL_DDK,
            hardwareVerification = Required(useRecoveryPubKey = true)
          )
        )

      is SealingDelegatedDecryptionKeyData ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            transaction = props.completingRecoveryData.nfcTransaction,
            screenPresentationStyle = props.presentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.APP_DELAY_NOTIFY_SEAL_DDK,
            hardwareVerification = Required(useRecoveryPubKey = true)
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

      is DelegatedDecryptionKeyErrorStateData ->
        ErrorFormBodyModel(
          title = "Unable to fetch Recovery Contact & inheritance data",
          subline =
            """
            Make sure you are connected to the internet and try again. You may choose to remove
            Recovery Contacts and inheritance relationships, which will not cause funds to be lost,
            but Recovery Contact and inheritance will need to be setup again.
            """.trimIndent(),
          primaryButton =
            ButtonDataModel(
              text = "Retry",
              onClick = props.completingRecoveryData.onRetry
            ),
          secondaryButton =
            ButtonDataModel(
              text = "Remove Recovery Contacts & Inheritance data",
              onClick = props.completingRecoveryData.onContinue
            ),
          errorData = ErrorData(
            segment = when (props.completingRecoveryData.physicalFactor) {
              App -> RecoverySegment.DelayAndNotify.LostApp.Completion
              Hardware -> RecoverySegment.DelayAndNotify.LostHardware.Completion
            },
            actionDescription = "Fetching and restoring delegated decryption key",
            cause = props.completingRecoveryData.cause
          ),
          eventTrackerScreenId = DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_DDK_LOADING_ERROR
        ).asScreen(props.presentationStyle)

      is AwaitingHardwareProofOfPossessionData ->
        proofOfPossessionNfcStateMachine.model(
          ProofOfPossessionNfcProps(
            Request.HwKeyProof(
              onSuccess = props.completingRecoveryData.addHwFactorProofOfPossession
            ),
            fullAccountId = props.completingRecoveryData.fullAccountId,
            appAuthKey = props.completingRecoveryData.appAuthKey,
            onBack = props.completingRecoveryData.rollback,
            hardwareVerification = Required(useRecoveryPubKey = true),
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
            actionDescription = "Fetching Recovery Contacts to complete recovery",
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

      is PerformingSweepData -> {
        val scope = rememberStableCoroutineScope()
        sweepUiStateMachine.model(
          SweepUiProps(
            presentationStyle = props.presentationStyle,
            onExit = props.completingRecoveryData.rollback,
            onSuccess = {
              scope.launch {
                // Set the flag to no longer show the replace hardware card nudge
                // this flag is used by the MoneyHomeCardsUiStateMachine
                // and toggled on by the FullAccountCloudBackupRestorationUiStateMachine
                postSocRecTaskRepository.setHardwareReplacementNeeded(false)
                recoveryStatusService
                  .setLocalRecoveryProgress(
                    SweptFunds(props.completingRecoveryData.keybox)
                  )
                props.onComplete?.invoke()
              }
            },
            keybox = props.completingRecoveryData.keybox,
            recoveredFactor = props.completingRecoveryData.physicalFactor
          )
        )
      }

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

      is PerformingDdkBackupData ->
        LoadingBodyModel(
          message = "Updating backup...",
          id =
            props.completingRecoveryData.physicalFactor.getEventId(
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_DDK_UPLOAD,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_DDK_UPLOAD
            ),
          eventTrackerShouldTrack = false
        ).asScreen(props.presentationStyle)

      is FailedPerformingDdkBackupData ->
        ErrorFormBodyModelWithOptionalErrorData(
          title = "We were unable to update backup",
          subline = StringModel("Please try again."),
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
              DelayNotifyRecoveryEventTrackerScreenId.LOST_APP_DELAY_NOTIFY_DDK_UPLOAD_FAILURE,
              HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_DDK_UPLOAD_FAILURE
            ),
          eventTrackerShouldTrack = false
        ).asScreen(props.presentationStyle)

      is FailedPerformingCloudBackupData ->
        ErrorFormBodyModelWithOptionalErrorData(
          title = "We were unable to upload backup",
          subline = StringModel("Please try again."),
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
