package build.wallet.statemachine.data.recovery.losthardware.initiate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.error.F8eError
import build.wallet.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED
import build.wallet.f8e.error.code.InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryService
import build.wallet.keybox.builder.KeyCrossBuilder
import build.wallet.recovery.LostHardwareRecoveryStarter
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.StartingLostAppRecoveryData.InitiatingLostAppRecoveryData.CancellingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingHardwareProofOfPossessionKeyData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.AwaitingNewHardwareData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.DisplayingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.FailedInitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.InitiatingRecoveryWithF8eData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.VerifyingNotificationCommsData
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.AwaitingHardwareProofOfPossessionState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.CancellingConflictingRecoveryWithF8eState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.CreatingKeyCrossState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.DisplayingConflictingRecoveryState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.FailedInitiatingServerRecoveryState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.FailedToCancelConflictingRecoveryWithF8EState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.InitiatingServerRecoveryState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.OnboardingNewHardware
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.VerifyingNotificationCommsState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.VerifyingNotificationCommsState.VerifyingNotificationCommsForCancellationState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.VerifyingNotificationCommsState.VerifyingNotificationCommsForInitiationState
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataProps
import build.wallet.statemachine.data.recovery.verification.RecoveryNotificationVerificationDataStateMachine
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

/** Data State Machine for execution initiation of lost hardware recovery. */
interface InitiatingLostHardwareRecoveryDataStateMachine :
  StateMachine<InitiatingLostHardwareRecoveryProps, InitiatingLostHardwareRecoveryData>

data class InitiatingLostHardwareRecoveryProps(
  val account: FullAccount,
)

class InitiatingLostHardwareRecoveryDataStateMachineImpl(
  private val keyCrossBuilder: KeyCrossBuilder,
  private val lostHardwareRecoveryStarter: LostHardwareRecoveryStarter,
  private val cancelDelayNotifyRecoveryService: CancelDelayNotifyRecoveryService,
  private val recoveryNotificationVerificationDataStateMachine:
    RecoveryNotificationVerificationDataStateMachine,
) : InitiatingLostHardwareRecoveryDataStateMachine {
  @Composable
  override fun model(
    props: InitiatingLostHardwareRecoveryProps,
  ): InitiatingLostHardwareRecoveryData {
    var state: State by remember { mutableStateOf(State.OnboardingNewHardware) }

    return when (val s = state) {
      OnboardingNewHardware ->
        AwaitingNewHardwareData(
          addHardwareKeys = { sealedCsek, keyBundle ->
            state =
              CreatingKeyCrossState(
                sealedCsek = sealedCsek,
                keyBundle = keyBundle
              )
          }
        )

      is CreatingKeyCrossState -> {
        LaunchedEffect("building-key-cross-with-hardware-keys") {
          val keyCrossDraft = keyCrossBuilder.createNewKeyCross(props.account.keybox.config)
          val keyCrossDraftWithAddedKeys =
            keyCrossBuilder.addHardwareKeyBundle(
              draft = keyCrossDraft,
              hardwareKeyBundle = s.keyBundle
            )
          state =
            InitiatingServerRecoveryState(
              destinationAppKeyBundle = keyCrossDraftWithAddedKeys.appKeyBundle,
              destinationHardwareKeyBundle = keyCrossDraftWithAddedKeys.hardwareKeyBundle
            )
        }
        InitiatingRecoveryWithF8eData(
          rollback = {
            state = OnboardingNewHardware
          }
        )
      }

      is InitiatingServerRecoveryState -> {
        LaunchedEffect("initiating-lost-hardware-server-recovery") {
          lostHardwareRecoveryStarter
            .initiate(
              activeKeybox = props.account.keybox,
              destinationAppKeyBundle = s.destinationAppKeyBundle,
              destinationHardwareKeyBundle = s.destinationHardwareKeyBundle
            )
            .onFailure { error ->
              if (error.isServerInitiationError(COMMS_VERIFICATION_REQUIRED)) {
                state =
                  VerifyingNotificationCommsForInitiationState(
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle
                  )
              } else if (error.isServerInitiationError(RECOVERY_ALREADY_EXISTS)) {
                state =
                  DisplayingConflictingRecoveryState(
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle
                  )
              } else {
                state =
                  // Otherwise, show a failure
                  FailedInitiatingServerRecoveryState(
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle
                  )
              }
            }
        }
        InitiatingRecoveryWithF8eData(
          rollback = {
            state = OnboardingNewHardware
          }
        )
      }

      is FailedInitiatingServerRecoveryState -> {
        FailedInitiatingRecoveryWithF8eData(
          retry = {
            state =
              InitiatingServerRecoveryState(
                destinationAppKeyBundle = s.destinationAppKeyBundle,
                destinationHardwareKeyBundle = s.destinationHardwareKeyBundle
              )
          },
          rollback = {
            state = OnboardingNewHardware
          }
        )
      }

      is VerifyingNotificationCommsState ->
        VerifyingNotificationCommsData(
          data =
            recoveryNotificationVerificationDataStateMachine.model(
              props =
                RecoveryNotificationVerificationDataProps(
                  f8eEnvironment = props.account.config.f8eEnvironment,
                  fullAccountId = props.account.accountId,
                  onRollback = {
                    state = OnboardingNewHardware
                  },
                  onComplete = {
                    state =
                      when (s) {
                        is VerifyingNotificationCommsForCancellationState -> {
                          CancellingConflictingRecoveryWithF8eState(
                            destinationAppKeyBundle = s.destinationAppKeyBundle,
                            destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                            hwFactorProofOfPossession = s.hwFactorProofOfPossession
                          )
                        }

                        is VerifyingNotificationCommsForInitiationState -> {
                          InitiatingServerRecoveryState(
                            destinationAppKeyBundle = s.destinationAppKeyBundle,
                            destinationHardwareKeyBundle = s.destinationHardwareKeyBundle
                          )
                        }
                      }
                  },
                  hwFactorProofOfPossession = null,
                  lostFactor = Hardware
                )
            )
        )

      is DisplayingConflictingRecoveryState ->
        DisplayingConflictingRecoveryData(
          onCancelRecovery = {
            state =
              AwaitingHardwareProofOfPossessionState(
                s.destinationAppKeyBundle,
                s.destinationHardwareKeyBundle
              )
          }
        )

      is CancellingConflictingRecoveryWithF8eState -> {
        LaunchedEffect("cancelling-existing-recovery") {
          cancelDelayNotifyRecoveryService.cancel(
            props.account.config.f8eEnvironment,
            props.account.accountId,
            s.hwFactorProofOfPossession
          ).onSuccess {
            state =
              InitiatingServerRecoveryState(
                destinationAppKeyBundle = s.destinationAppKeyBundle,
                destinationHardwareKeyBundle = s.destinationHardwareKeyBundle
              )
          }
            .onFailure {
              val f8eError = it as F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
              state =
                if (f8eError.errorCode == CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED) {
                  VerifyingNotificationCommsForCancellationState(
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    hwFactorProofOfPossession = s.hwFactorProofOfPossession
                  )
                } else {
                  FailedToCancelConflictingRecoveryWithF8EState(
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    hwFactorProofOfPossession = s.hwFactorProofOfPossession
                  )
                }
            }
        }
        InitiatingLostHardwareRecoveryData.CancellingConflictingRecoveryData
      }

      is AwaitingHardwareProofOfPossessionState ->
        AwaitingHardwareProofOfPossessionKeyData(
          onComplete = {
            state =
              CancellingConflictingRecoveryWithF8eState(
                destinationAppKeyBundle = s.destinationAppKeyBundle,
                destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                hwFactorProofOfPossession = it
              )
          },
          rollback = {
            state = OnboardingNewHardware
          }
        )

      is FailedToCancelConflictingRecoveryWithF8EState ->
        InitiatingLostHardwareRecoveryData.FailedToCancelConflictingRecoveryData(
          onAcknowledge = {
            state = OnboardingNewHardware
          }
        )
    }
  }

  private sealed interface State {
    data object OnboardingNewHardware : State

    data class CreatingKeyCrossState(
      val sealedCsek: SealedCsek,
      val keyBundle: HwKeyBundle,
    ) : State

    data class InitiatingServerRecoveryState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
    ) : State

    data class FailedInitiatingServerRecoveryState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
    ) : State

    sealed interface VerifyingNotificationCommsState : State {
      val destinationAppKeyBundle: AppKeyBundle
      val destinationHardwareKeyBundle: HwKeyBundle

      data class VerifyingNotificationCommsForInitiationState(
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
      ) : VerifyingNotificationCommsState

      data class VerifyingNotificationCommsForCancellationState(
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
        val hwFactorProofOfPossession: HwFactorProofOfPossession,
      ) : VerifyingNotificationCommsState
    }

    data class DisplayingConflictingRecoveryState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class CancellingConflictingRecoveryWithF8eState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class FailedToCancelConflictingRecoveryWithF8EState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
    ) : State

    data class AwaitingHardwareProofOfPossessionState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
    ) : State
  }
}

private fun LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.isServerInitiationError(
  type: InitiateAccountDelayNotifyErrorCode,
): Boolean {
  if (this !is F8eInitiateDelayNotifyError) {
    return false
  }

  if (error !is F8eError.SpecificClientError) {
    return false
  }

  val f8eError = error as F8eError.SpecificClientError<InitiateAccountDelayNotifyErrorCode>
  return f8eError.errorCode == type
}

/**
 * Comms verification could be required for multiple actions, enumerated here.
 */
private sealed interface CommsVerificationTargetAction {
  data object CancelRecovery : CommsVerificationTargetAction

  data object InitiateRecovery : CommsVerificationTargetAction
}
