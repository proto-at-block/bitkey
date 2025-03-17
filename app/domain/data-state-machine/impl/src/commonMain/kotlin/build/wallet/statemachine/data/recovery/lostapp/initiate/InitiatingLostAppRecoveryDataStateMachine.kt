package build.wallet.statemachine.data.recovery.lostapp.initiate

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.bitkey.recovery.HardwareKeysForRecovery
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.AuthF8eClient.InitiateAuthenticationSuccess
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.platform.random.UuidGenerator
import build.wallet.recovery.CancelDelayNotifyRecoveryError.F8eCancelDelayNotifyError
import build.wallet.recovery.LostAppAndCloudRecoveryService
import build.wallet.recovery.LostAppAndCloudRecoveryService.CompletedAuth
import build.wallet.recovery.LostAppRecoveryInitiator
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.*
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
  StateMachine<InitiatingLostAppRecoveryProps, InitiatingLostAppRecoveryData>

data class InitiatingLostAppRecoveryProps(
  val onRollback: () -> Unit,
)

@BitkeyInject(AppScope::class)
class InitiatingLostAppRecoveryDataStateMachineImpl(
  private val lostAppRecoveryInitiator: LostAppRecoveryInitiator,
  private val lostAppAndCloudRecoveryService: LostAppAndCloudRecoveryService,
  private val uuidGenerator: UuidGenerator,
  private val minimumLoadingDuration: MinimumLoadingDuration,
  private val accountConfigService: AccountConfigService,
) : InitiatingLostAppRecoveryDataStateMachine {
  @Composable
  override fun model(props: InitiatingLostAppRecoveryProps): InitiatingLostAppRecoveryData {
    var state: State by remember { mutableStateOf(AwaitingHardwareKeysState) }
    val defaultConfig = remember { accountConfigService.defaultConfig().value }

    return state.let {
      when (val dataState = it) {
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
              .initiateAuth(dataState.hardwareAuthKey)
              .onSuccess { authChallenge ->
                state = AwaitingHardwareSignedAuthChallengeState(
                  authChallenge = authChallenge,
                  hardwareAuthKey = dataState.hardwareAuthKey
                )
              }
              .onFailure { error ->
                state = FailedToInitiateHardwareAuthWithF8eState(dataState.hardwareAuthKey, error)
              }
          }

          InitiatingAppAuthWithF8eData(
            rollback = props.onRollback
          )
        }

        is FailedToInitiateHardwareAuthWithF8eState ->
          FailedToInitiateAppAuthWithF8eData(
            error = dataState.error,
            retry = {
              state = InitiatingHardwareAuthWithF8eState(dataState.hardwareAuthKey)
            },
            rollback = props.onRollback
          )

        is AwaitingHardwareSignedAuthChallengeState ->
          AwaitingAppSignedAuthChallengeData(
            addSignedChallenge = { hardwareSignedChallenge ->
              state = AuthenticatingWithF8eViaHardwareState(
                authChallenge = dataState.authChallenge,
                hardwareAuthKey = dataState.hardwareAuthKey,
                signedAuthChallenge = hardwareSignedChallenge
              )
            },
            challenge = dataState.authChallenge,
            rollback = {
              state = AwaitingHardwareKeysState
            }
          )

        is AuthenticatingWithF8eViaHardwareState -> {
          LaunchedEffect("authenticate-with-hardware") {
            withMinimumDelay(minimumLoadingDuration.value) {
              lostAppAndCloudRecoveryService
                .completeAuth(
                  accountId = FullAccountId(dataState.authChallenge.accountId),
                  session = dataState.authChallenge.session,
                  hwSignedChallenge = dataState.signedAuthChallenge,
                  hwAuthKey = dataState.hardwareAuthKey
                )
            }
              .onSuccess { completedAuth ->
                state = AwaitingHardwareProofOfPossessionAndSpendingKeyState(
                  authChallenge = dataState.authChallenge,
                  completedAuth = completedAuth,
                  signedAuthChallenge = dataState.signedAuthChallenge,
                  hardwareAuthKey = dataState.hardwareAuthKey
                )
              }
              .onFailure { error ->
                state =
                  FailedToAuthenticateWithF8eViaHardwareState(
                    authChallenge = dataState.authChallenge,
                    hardwareAuthKey = dataState.hardwareAuthKey,
                    signedAuthChallenge = dataState.signedAuthChallenge,
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
                  authChallenge = dataState.authChallenge,
                  hardwareAuthKey = dataState.hardwareAuthKey,
                  signedAuthChallenge = dataState.signedAuthChallenge
                )
            },
            error = dataState.error,
            rollback = props.onRollback
          )

        is AwaitingHardwareProofOfPossessionAndSpendingKeyState -> {
          AwaitingHardwareProofOfPossessionAndKeysData(
            completedAuth = dataState.completedAuth,
            onComplete = { proof, hardwareSpendingKey, newAppGlobalAuthKeyHwSignature ->
              state = AwaitingPushNotificationPermissionState(
                signedAuthChallenge = dataState.signedAuthChallenge,
                completedAuth = dataState.completedAuth,
                newAppGlobalAuthKeyHwSignature = newAppGlobalAuthKeyHwSignature,
                hardwareKeys = HardwareKeysForRecovery(
                  newKeyBundle = HwKeyBundle(
                    localId = uuidGenerator.random(),
                    spendingKey = hardwareSpendingKey,
                    authKey = dataState.hardwareAuthKey,
                    networkType = defaultConfig.bitcoinNetworkType
                  )
                ),
                hwFactorProofOfPossession = proof
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
                completedAuth = dataState.completedAuth,
                hardwareKeys = dataState.hardwareKeys,
                newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                signedAuthChallenge = dataState.signedAuthChallenge,
                hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
              )
            },
            onRetreat = {
              state = AwaitingHardwareKeysState
            }
          )
        }
        is InitiatingRecoveryWithF8eState -> {
          LaunchedEffect("initiate-recovery") {
            lostAppRecoveryInitiator
              .initiate(
                newAppKeys = dataState.completedAuth.destinationAppKeys,
                hardwareKeysForRecovery = dataState.hardwareKeys,
                appGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                fullAccountId = dataState.completedAuth.accountId,
                hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
              )
              .onFailure { error ->
                if (error.isServerInitiationError(COMMS_VERIFICATION_REQUIRED)) {
                  state = VerifyingNotificationCommsState(
                    completedAuth = dataState.completedAuth,
                    newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                    signedAuthChallenge = dataState.signedAuthChallenge,
                    hardwareKeys = dataState.hardwareKeys,
                    hwFactorProofOfPossession = dataState.hwFactorProofOfPossession,
                    targetAction = InitiateRecovery
                  )
                } else if (error.isServerInitiationError(RECOVERY_ALREADY_EXISTS)) {
                  state = DisplayingConflictingRecoveryState(
                    completedAuth = dataState.completedAuth,
                    newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                    signedAuthChallenge = dataState.signedAuthChallenge,
                    hardwareKeys = dataState.hardwareKeys,
                    hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                  )
                } else {
                  state = FailedToInitiateRecoveryWithF8eState(
                    completedAuth = dataState.completedAuth,
                    newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                    signedAuthChallenge = dataState.signedAuthChallenge,
                    hardwareKeys = dataState.hardwareKeys,
                    hwFactorProofOfPossession = dataState.hwFactorProofOfPossession,
                    error = error
                  )
                }
              }
          }

          InitiatingLostAppRecoveryWithF8eData(rollback = props.onRollback)
        }

        is FailedToInitiateRecoveryWithF8eState ->
          FailedToInitiateLostAppWithF8eData(
            error = dataState.error,
            retry = {
              state = InitiatingRecoveryWithF8eState(
                completedAuth = dataState.completedAuth,
                newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                signedAuthChallenge = dataState.signedAuthChallenge,
                hardwareKeys = dataState.hardwareKeys,
                hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
              )
            },
            rollback = props.onRollback
          )

        is VerifyingNotificationCommsState ->
          VerifyingNotificationCommsData(
            fullAccountId = dataState.completedAuth.accountId,
            onRollback = props.onRollback,
            onComplete = {
              // We try our target action on F8e again, now that the additional verification
              // is complete
              state =
                when (dataState.targetAction) {
                  InitiateRecovery ->
                    InitiatingRecoveryWithF8eState(
                      completedAuth = dataState.completedAuth,
                      newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hardwareKeys = dataState.hardwareKeys,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                    )

                  CancelRecovery ->
                    CancellingConflictingRecoveryWithF8eState(
                      completedAuth = dataState.completedAuth,
                      newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hardwareKeys = dataState.hardwareKeys,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                    )
                }
            },
            hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
          )

        is CancellingConflictingRecoveryWithF8eState -> {
          LaunchedEffect("cancel-existing-recovery") {
            lostAppAndCloudRecoveryService
              .cancelRecovery(
                accountId = dataState.completedAuth.accountId,
                hwProofOfPossession = dataState.hwFactorProofOfPossession
              )
              .onSuccess {
                state = InitiatingRecoveryWithF8eState(
                  completedAuth = dataState.completedAuth,
                  hardwareKeys = dataState.hardwareKeys,
                  newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                  signedAuthChallenge = dataState.signedAuthChallenge,
                  hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
                )
              }
              .onFailure {
                // TODO: move error handling details to service implementation
                val f8eError =
                  ((it as? F8eCancelDelayNotifyError)?.error as? F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>)
                state =
                  if (f8eError?.errorCode == CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED) {
                    VerifyingNotificationCommsState(
                      completedAuth = dataState.completedAuth,
                      newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hardwareKeys = dataState.hardwareKeys,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession,
                      targetAction = CancelRecovery
                    )
                  } else {
                    FailedToCancelConflictingRecoveryState(
                      error = it,
                      hardwareKeys = dataState.hardwareKeys,
                      newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                      completedAuth = dataState.completedAuth,
                      signedAuthChallenge = dataState.signedAuthChallenge,
                      hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
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
                completedAuth = dataState.completedAuth,
                hardwareKeys = dataState.hardwareKeys,
                newAppGlobalAuthKeyHwSignature = dataState.newAppGlobalAuthKeyHwSignature,
                signedAuthChallenge = dataState.signedAuthChallenge,
                hwFactorProofOfPossession = dataState.hwFactorProofOfPossession
              )
            },
            onRetreat = props.onRollback
          )

        is FailedToCancelConflictingRecoveryState ->
          FailedToCancelConflictingRecoveryData(
            cause = dataState.error,
            onAcknowledge = { state = AwaitingHardwareKeysState }
          )
      }
    }
  }

  private sealed interface State {
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
      val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class CancellingConflictingRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * [CancellingConflictingRecoveryWithF8eState] failed.
     */
    data class FailedToCancelConflictingRecoveryState(
      val error: Error,
      val hardwareKeys: HardwareKeysForRecovery,
      val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Corresponds to [InitiatingLostAppRecoveryWithF8eData].
     */
    data class InitiatingRecoveryWithF8eState(
      val completedAuth: CompletedAuth,
      val hardwareKeys: HardwareKeysForRecovery,
      val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    data class DisplayingConflictingRecoveryState(
      val completedAuth: CompletedAuth,
      val hardwareKeys: HardwareKeysForRecovery,
      val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * [InitiatingRecoveryWithF8eState] failed.
     */
    data class FailedToInitiateRecoveryWithF8eState(
      val hardwareKeys: HardwareKeysForRecovery,
      val completedAuth: CompletedAuth,
      val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val error: Error,
    ) : State

    data class VerifyingNotificationCommsState(
      val hardwareKeys: HardwareKeysForRecovery,
      val newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      val completedAuth: CompletedAuth,
      val signedAuthChallenge: String,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
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

private fun LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.isServerInitiationError(
  type: InitiateAccountDelayNotifyErrorCode,
): Boolean {
  if (this !is LostAppRecoveryInitiator.InitiateDelayNotifyAppRecoveryError.F8eInitiateDelayNotifyError) {
    return false
  }

  if (error !is F8eError.SpecificClientError) {
    return false
  }

  val f8eError = error as F8eError.SpecificClientError<InitiateAccountDelayNotifyErrorCode>
  return f8eError.errorCode == type
}
