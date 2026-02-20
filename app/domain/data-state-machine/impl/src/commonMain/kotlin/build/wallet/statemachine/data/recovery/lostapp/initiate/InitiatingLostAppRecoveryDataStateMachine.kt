package build.wallet.statemachine.data.recovery.lostapp.initiate

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.recovery.InitiateDelayNotifyRecoveryError.*
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.cloud.backup.CloudBackup
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.LostAppAndCloudRecoveryService
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.InitiatingLostAppRecoveryData.*
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.InitiatingLostAppRecoveryData.AttemptingCloudRecoveryLostAppRecoveryDataData
import build.wallet.statemachine.data.recovery.lostapp.initiate.CommsVerificationTargetAction.CancelRecovery
import build.wallet.statemachine.data.recovery.lostapp.initiate.CommsVerificationTargetAction.InitiateRecovery
import build.wallet.statemachine.data.recovery.lostapp.initiate.InitiatingLostAppRecoveryDataStateMachineImpl.State.*
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

/**
 * Data state machine for initiating Delay * Notify recovery for Lost App case.
 *
 * Note: is not currently persistent, all states are in memory - relaunching the app during
 * initiation process will not restore latest initiation state.
 */
interface InitiatingLostAppRecoveryDataStateMachine :
  StateMachine<InitiatingLostAppRecoveryProps, LostAppRecoveryData>

data class InitiatingLostAppRecoveryProps(
  /**
   * List of cloud backups to try during recovery. The restoration flow will
   * attempt to decrypt each backup with the hardware key until one succeeds.
   */
  val cloudBackups: List<CloudBackup>,
  val onRollback: () -> Unit,
  val goToLiteAccountCreation: () -> Unit,
)

@BitkeyInject(AppScope::class)
class InitiatingLostAppRecoveryDataStateMachineImpl(
  private val lostAppAndCloudRecoveryService: LostAppAndCloudRecoveryService,
  private val uuidGenerator: UuidGenerator,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val accountConfigService: AccountConfigService,
) : InitiatingLostAppRecoveryDataStateMachine {
  @Composable
  override fun model(props: InitiatingLostAppRecoveryProps): LostAppRecoveryData {
    var state: State by remember {
      mutableStateOf(
        if (props.cloudBackups.isEmpty()) {
          AwaitingHardwareKeysState
        } else {
          AttemptingCloudBackupRecoveryState(props.cloudBackups)
        }
      )
    }
    val defaultConfig = remember { accountConfigService.defaultConfig().value }

    return when (val currentState = state) {
      is AttemptingCloudBackupRecoveryState ->
        AttemptingCloudRecoveryLostAppRecoveryDataData(
          cloudBackups = currentState.cloudBackups,
          rollback = props.onRollback,
          onRecoverAppKey = {
            state = AwaitingHardwareKeysState
          },
          goToLiteAccountCreation = props.goToLiteAccountCreation
        )

      is AwaitingHardwareKeysState -> {
        AwaitingHwKeysData(
          addHardwareAuthKey = { hardwareAuthKey ->
            state = InitiatingHardwareAuthWithF8eState(hardwareAuthKey)
          },
          rollback = props.onRollback
        )
      }

      is InitiatingHardwareAuthWithF8eState -> {
        LaunchedEffect("request-challenge") {
          lostAppAndCloudRecoveryService
            .initiateAuth(currentState.hardwareAuthKey)
            .onSuccess { authChallenge ->
              state = AwaitingHardwareSignedAuthChallengeState(
                authChallenge = authChallenge,
                hardwareAuthKey = currentState.hardwareAuthKey
              )
            }
            .onFailure { error ->
              state = FailedToInitiateHardwareAuthWithF8eState(currentState.hardwareAuthKey, error)
            }
        }

        InitiatingAppAuthWithF8eData(
          rollback = props.onRollback
        )
      }

      is FailedToInitiateHardwareAuthWithF8eState ->
        FailedToInitiateAppAuthWithF8eData(
          error = currentState.error,
          retry = {
            state = InitiatingHardwareAuthWithF8eState(currentState.hardwareAuthKey)
          },
          rollback = props.onRollback
        )

      is AwaitingHardwareSignedAuthChallengeState ->
        AwaitingAppSignedAuthChallengeData(
          addSignedChallenge = { hardwareSignedChallenge ->
            state = AuthenticatingWithF8eViaHardwareState(
              authChallenge = currentState.authChallenge,
              hardwareAuthKey = currentState.hardwareAuthKey,
              signedAuthChallenge = hardwareSignedChallenge
            )
          },
          challenge = currentState.authChallenge,
          rollback = {
            state = AwaitingHardwareKeysState
          }
        )

      is AuthenticatingWithF8eViaHardwareState -> {
        LaunchedEffect("authenticate-with-hardware") {
          withMinimumDelay(minimumLoadingDuration.value) {
            lostAppAndCloudRecoveryService
              .completeAuth(
                accountId = FullAccountId(currentState.authChallenge.accountId),
                session = currentState.authChallenge.session,
                hwSignedChallenge = currentState.signedAuthChallenge,
                hwAuthKey = currentState.hardwareAuthKey
              )
          }
            .onSuccess { completedAuth ->
              state = AwaitingHardwareProofOfPossessionAndSpendingKeyState(
                authChallenge = currentState.authChallenge,
                completedAuth = completedAuth,
                signedAuthChallenge = currentState.signedAuthChallenge,
                hardwareAuthKey = currentState.hardwareAuthKey
              )
            }
            .onFailure { error ->
              state =
                FailedToAuthenticateWithF8eViaHardwareState(
                  authChallenge = currentState.authChallenge,
                  hardwareAuthKey = currentState.hardwareAuthKey,
                  signedAuthChallenge = currentState.signedAuthChallenge,
                  error = error
                )
            }
        }
        AuthenticatingWithF8EViaAppData(
          rollback = props.onRollback
        )
      }

      is FailedToAuthenticateWithF8eViaHardwareState ->
        FailedToAuthenticateWithF8EViaAppData(
          retry = {
            state =
              AuthenticatingWithF8eViaHardwareState(
                authChallenge = currentState.authChallenge,
                hardwareAuthKey = currentState.hardwareAuthKey,
                signedAuthChallenge = currentState.signedAuthChallenge
              )
          },
          error = currentState.error,
          rollback = props.onRollback
        )

      is AwaitingHardwareProofOfPossessionAndSpendingKeyState -> {
        AwaitingHardwareProofOfPossessionAndKeysData(
          completedAuth = currentState.completedAuth,
          onComplete = { proof, hardwareSpendingKey, newAppGlobalAuthKeyHwSignature ->
            state = AwaitingPushNotificationPermissionState(
              signedAuthChallenge = currentState.signedAuthChallenge,
              completedAuth = currentState.completedAuth,
              hardwareKeys = HardwareKeysForRecovery(
                newAppGlobalAuthKeyHwSignature = newAppGlobalAuthKeyHwSignature,
                hwProofOfPossession = proof,
                newKeyBundle = HwKeyBundle(
                  localId = uuidGenerator.random(),
                  spendingKey = hardwareSpendingKey,
                  authKey = currentState.hardwareAuthKey,
                  networkType = defaultConfig.bitcoinNetworkType
                )
              )
            )
          },
          rollback = {
            state = AwaitingHardwareKeysState
          }
        )
      }

      is AwaitingPushNotificationPermissionState -> {
        AwaitingPushNotificationPermissionData(
          onComplete = {
            state = InitiatingRecoveryWithF8eState(
              completedAuth = currentState.completedAuth,
              hardwareKeys = currentState.hardwareKeys,
              signedAuthChallenge = currentState.signedAuthChallenge
            )
          },
          onRetreat = {
            state = AwaitingHardwareKeysState
          }
        )
      }
      is InitiatingRecoveryWithF8eState -> {
        LaunchedEffect("initiate-recovery") {
          lostAppAndCloudRecoveryService
            .initiateRecovery(
              completedAuth = currentState.completedAuth,
              hardwareKeysForRecovery = currentState.hardwareKeys
            )
            .onFailure { error ->
              when (error) {
                is CommsVerificationRequiredError ->
                  state = VerifyingNotificationCommsState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    targetAction = InitiateRecovery
                  )
                is RecoveryAlreadyExistsError ->
                  state = DisplayingConflictingRecoveryState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys
                  )
                is OtherError ->
                  state = FailedToInitiateRecoveryWithF8eState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    error = error
                  )
              }
            }
        }

        InitiatingLostAppRecoveryWithF8eData(rollback = props.onRollback)
      }

      is FailedToInitiateRecoveryWithF8eState ->
        FailedToInitiateLostAppWithF8eData(
          error = currentState.error,
          retry = {
            state = InitiatingRecoveryWithF8eState(
              completedAuth = currentState.completedAuth,
              signedAuthChallenge = currentState.signedAuthChallenge,
              hardwareKeys = currentState.hardwareKeys
            )
          },
          rollback = props.onRollback
        )

      is VerifyingNotificationCommsState ->
        VerifyingNotificationCommsData(
          fullAccountId = currentState.completedAuth.accountId,
          onRollback = props.onRollback,
          onComplete = {
            // We try our target action on F8e again, now that the additional verification
            // is complete
            state =
              when (currentState.targetAction) {
                InitiateRecovery ->
                  InitiatingRecoveryWithF8eState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys
                  )

                CancelRecovery ->
                  CancellingConflictingRecoveryWithF8eState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys
                  )
              }
          },
          hwFactorProofOfPossession = currentState.hardwareKeys.hwProofOfPossession
        )

      is CancellingConflictingRecoveryWithF8eState -> {
        LaunchedEffect("cancel-existing-recovery") {
          lostAppAndCloudRecoveryService
            .cancelRecovery(
              accountId = currentState.completedAuth.accountId,
              hwProofOfPossession = currentState.hardwareKeys.hwProofOfPossession
            )
            .onSuccess {
              state = InitiatingRecoveryWithF8eState(
                completedAuth = currentState.completedAuth,
                hardwareKeys = currentState.hardwareKeys,
                signedAuthChallenge = currentState.signedAuthChallenge
              )
            }
            .onFailure {
              // TODO: move error handling details to service implementation
              val f8eError =
                ((it as? F8eCancelDelayNotifyError)?.error as? F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>)
              state =
                if (f8eError?.errorCode == CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED) {
                  VerifyingNotificationCommsState(
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge,
                    hardwareKeys = currentState.hardwareKeys,
                    targetAction = CancelRecovery
                  )
                } else {
                  FailedToCancelConflictingRecoveryState(
                    error = it,
                    hardwareKeys = currentState.hardwareKeys,
                    completedAuth = currentState.completedAuth,
                    signedAuthChallenge = currentState.signedAuthChallenge
                  )
                }
            }
        }
        CancellingConflictingRecoveryData
      }

      is DisplayingConflictingRecoveryState ->
        DisplayingConflictingRecoveryData(
          onCancelRecovery = {
            state = CancellingConflictingRecoveryWithF8eState(
              completedAuth = currentState.completedAuth,
              hardwareKeys = currentState.hardwareKeys,
              signedAuthChallenge = currentState.signedAuthChallenge
            )
          },
          onRetreat = props.onRollback
        )

      is FailedToCancelConflictingRecoveryState ->
        FailedToCancelConflictingRecoveryData(
          cause = currentState.error,
          onAcknowledge = { state = AwaitingHardwareKeysState }
        )
    }
  }

  private sealed interface State {
    data class AttemptingCloudBackupRecoveryState(
      val cloudBackups: List<CloudBackup>,
    ) : State

    /**
     * Corresponds to [AwaitingHwKeysData].
     */
    data object AwaitingHardwareKeysState : State

    /**
     * Corresponds to [InitiatingAppAuthWithF8eData].
     */
    data class InitiatingHardwareAuthWithF8eState(
      val hardwareAuthKey: HwAuthPublicKey,
    ) : State

    /**
     * [InitiatingHardwareAuthWithF8eState] failed.
     */
    data class FailedToInitiateHardwareAuthWithF8eState(
      val hardwareAuthKey: HwAuthPublicKey,
      val error: Error,
    ) : State

    /**
     * Corresponds to [AwaitingAppSignedAuthChallengeData].
     */
    data class AwaitingHardwareSignedAuthChallengeState(
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: InitiateAuthenticationSuccess,
    ) : State

    data class AuthenticatingWithF8eViaHardwareState(
      val authChallenge: InitiateAuthenticationSuccess,
      val hardwareAuthKey: HwAuthPublicKey,
      val signedAuthChallenge: String,
    ) : State

    /**
     * [AuthenticatingWithF8eViaHardwareState] failed.
     */
    data class FailedToAuthenticateWithF8eViaHardwareState(
      val hardwareAuthKey: HwAuthPublicKey,
      val authChallenge: InitiateAuthenticationSuccess,
      val signedAuthChallenge: String,
      val error: Throwable,
    ) : State

    /**
     * Corresponds to [AwaitingAppSignedAuthChallengeData].
     */
    data class AwaitingHardwareProofOfPossessionAndSpendingKeyState(
      val authChallenge: InitiateAuthenticationSuccess,
      val completedAuth: CompletedAuth,
      val hardwareAuthKey: HwAuthPublicKey,
      val signedAuthChallenge: String,
    ) : State

    /**
     * Corresponds to [AwaitingPushNotificationPermissionData].
     */
    data class AwaitingPushNotificationPermissionState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class CancellingConflictingRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
    ) : State

    /**
     * [CancellingConflictingRecoveryWithF8eState] failed.
     */
    data class FailedToCancelConflictingRecoveryState(
      val error: Error,
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
    ) : State

    /**
     * Corresponds to [InitiatingLostAppRecoveryWithF8eData].
     */
    data class InitiatingRecoveryWithF8eState(
      val completedAuth: CompletedAuth,
      val hardwareKeys: HardwareKeysForRecovery,
      val signedAuthChallenge: String,
    ) : State

    data class DisplayingConflictingRecoveryState(
      val completedAuth: CompletedAuth,
      val hardwareKeys: HardwareKeysForRecovery,
      val signedAuthChallenge: String,
    ) : State

    /**
     * [InitiatingRecoveryWithF8eState] failed.
     */
    data class FailedToInitiateRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val error: Error,
    ) : State

    data class VerifyingNotificationCommsState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val targetAction: CommsVerificationTargetAction,
    ) : State
  }
}

/**
 * Comms verification could be required for multiple actions. These actions are enumerated here.
 */
sealed interface CommsVerificationTargetAction {
  data object CancelRecovery : CommsVerificationTargetAction

  data object InitiateRecovery : CommsVerificationTargetAction
}
