package build.wallet.statemachine.data.recovery.losthardware.initiate

import androidx.compose.runtime.*
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.COMMS_VERIFICATION_REQUIRED
import bitkey.f8e.error.code.InitiateAccountDelayNotifyErrorCode.RECOVERY_ALREADY_EXISTS
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.cloud.backup.csek.SealedCsek
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.recovery.LostHardwareRecoveryStarter
import build.wallet.recovery.LostHardwareRecoveryStarter.InitiateDelayNotifyHardwareRecoveryError.F8eInitiateDelayNotifyError
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.lostapp.LostAppRecoveryData.LostAppRecoveryHaveNotStartedData.InitiatingLostAppRecoveryData.CancellingConflictingRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData.InitiatingLostHardwareRecoveryData.*
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.*
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.VerifyingNotificationCommsState.VerifyingNotificationCommsForCancellationState
import build.wallet.statemachine.data.recovery.losthardware.initiate.InitiatingLostHardwareRecoveryDataStateMachineImpl.State.VerifyingNotificationCommsState.VerifyingNotificationCommsForInitiationState
import build.wallet.time.MinimumLoadingDuration
import build.wallet.time.withMinimumDelay
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

/** Data State Machine for execution initiation of lost hardware recovery. */
interface InitiatingLostHardwareRecoveryDataStateMachine :
  StateMachine<InitiatingLostHardwareRecoveryProps, InitiatingLostHardwareRecoveryData>

data class InitiatingLostHardwareRecoveryProps(
  val account: FullAccount,
)

@BitkeyInject(AppScope::class)
class InitiatingLostHardwareRecoveryDataStateMachineImpl(
  private val appKeysGenerator: AppKeysGenerator,
  private val lostHardwareRecoveryStarter: LostHardwareRecoveryStarter,
  private val cancelDelayNotifyRecoveryF8eClient: CancelDelayNotifyRecoveryF8eClient,
  private val minimumLoadingDuration: MinimumLoadingDuration,
) : InitiatingLostHardwareRecoveryDataStateMachine {
  @Composable
  override fun model(
    props: InitiatingLostHardwareRecoveryProps,
  ): InitiatingLostHardwareRecoveryData {
    var state: State by remember { mutableStateOf(GeneratingNewAppKeys) }
    return when (val s = state) {
      is GeneratingNewAppKeys -> {
        LaunchedEffect("building-key-cross-with-hardware-keys") {
          withMinimumDelay(minimumLoadingDuration.value) {
            appKeysGenerator.generateKeyBundle()
          }
            .onSuccess {
              state = OnboardingNewHardware(it)
            }
            .onFailure {
              state = State.ErrorGeneratingNewAppKeysState(it)
            }
        }
        GeneratingNewAppKeysData
      }

      is State.ErrorGeneratingNewAppKeysState ->
        InitiatingLostHardwareRecoveryData.ErrorGeneratingNewAppKeysData(
          retry = { state = GeneratingNewAppKeys },
          cause = s.cause
        )

      is OnboardingNewHardware ->
        AwaitingNewHardwareData(
          newAppGlobalAuthKey = s.newAppKeys.authKey,
          addHardwareKeys = { sealedCsek, keyBundle, appGlobalAuthKeyHwSignature ->
            state = InitiatingServerRecoveryState(
              sealedCsek = sealedCsek,
              destinationAppKeyBundle = s.newAppKeys,
              destinationHardwareKeyBundle = keyBundle,
              appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
            )
          }
        )

      is InitiatingServerRecoveryState -> {
        LaunchedEffect("initiating-lost-hardware-server-recovery") {
          lostHardwareRecoveryStarter
            .initiate(
              destinationAppKeyBundle = s.destinationAppKeyBundle,
              destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
              appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
            )
            .onFailure { error ->
              if (error.isServerInitiationError(COMMS_VERIFICATION_REQUIRED)) {
                state =
                  VerifyingNotificationCommsForInitiationState(
                    sealedCsek = s.sealedCsek,
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
              } else if (error.isServerInitiationError(RECOVERY_ALREADY_EXISTS)) {
                state =
                  DisplayingConflictingRecoveryState(
                    sealedCsek = s.sealedCsek,
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
              } else {
                state =
                  // Otherwise, show a failure
                  FailedInitiatingServerRecoveryState(
                    cause = error,
                    sealedCsek = s.sealedCsek,
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
              }
            }
        }
        InitiatingRecoveryWithF8eData(
          rollback = {
            state = GeneratingNewAppKeys
          }
        )
      }

      is FailedInitiatingServerRecoveryState -> {
        FailedInitiatingRecoveryWithF8eData(
          cause = s.cause,
          retry = {
            state =
              InitiatingServerRecoveryState(
                sealedCsek = s.sealedCsek,
                destinationAppKeyBundle = s.destinationAppKeyBundle,
                destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
              )
          },
          rollback = {
            state = GeneratingNewAppKeys
          }
        )
      }

      is VerifyingNotificationCommsState ->
        VerifyingNotificationCommsData(
          fullAccountId = props.account.accountId,
          onRollback = {
            state = GeneratingNewAppKeys
          },
          onComplete = {
            state =
              when (s) {
                is VerifyingNotificationCommsForCancellationState -> {
                  CancellingConflictingRecoveryWithF8eState(
                    sealedCsek = s.sealedCsek,
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    hwFactorProofOfPossession = s.hwFactorProofOfPossession,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
                }

                is VerifyingNotificationCommsForInitiationState -> {
                  InitiatingServerRecoveryState(
                    sealedCsek = s.sealedCsek,
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
                }
              }
          }
        )

      is DisplayingConflictingRecoveryState ->
        DisplayingConflictingRecoveryData(
          onCancelRecovery = {
            state =
              AwaitingHardwareProofOfPossessionState(
                s.sealedCsek,
                s.destinationAppKeyBundle,
                s.destinationHardwareKeyBundle,
                s.appGlobalAuthKeyHwSignature
              )
          }
        )

      is CancellingConflictingRecoveryWithF8eState -> {
        LaunchedEffect("cancelling-existing-recovery") {
          cancelDelayNotifyRecoveryF8eClient.cancel(
            props.account.config.f8eEnvironment,
            props.account.accountId,
            s.hwFactorProofOfPossession
          ).onSuccess {
            state =
              InitiatingServerRecoveryState(
                sealedCsek = s.sealedCsek,
                destinationAppKeyBundle = s.destinationAppKeyBundle,
                destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
              )
          }
            .onFailure {
              val f8eError = it as F8eError.SpecificClientError<CancelDelayNotifyRecoveryErrorCode>
              state =
                if (f8eError.errorCode == CancelDelayNotifyRecoveryErrorCode.COMMS_VERIFICATION_REQUIRED) {
                  VerifyingNotificationCommsForCancellationState(
                    sealedCsek = s.sealedCsek,
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    hwFactorProofOfPossession = s.hwFactorProofOfPossession,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
                } else {
                  FailedToCancelConflictingRecoveryWithF8EState(
                    cause = f8eError.error,
                    sealedCsek = s.sealedCsek,
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    hwFactorProofOfPossession = s.hwFactorProofOfPossession,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
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
                sealedCsek = s.sealedCsek,
                destinationAppKeyBundle = s.destinationAppKeyBundle,
                destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature,
                hwFactorProofOfPossession = it
              )
          },
          rollback = {
            state = GeneratingNewAppKeys
          }
        )

      is FailedToCancelConflictingRecoveryWithF8EState ->
        InitiatingLostHardwareRecoveryData.FailedToCancelConflictingRecoveryData(
          cause = s.cause,
          onAcknowledge = {
            state = GeneratingNewAppKeys
          }
        )
    }
  }

  private sealed interface State {
    data class OnboardingNewHardware(
      val newAppKeys: AppKeyBundle,
    ) : State

    data object GeneratingNewAppKeys : State

    data class ErrorGeneratingNewAppKeysState(
      val cause: Throwable,
    ) : State

    data class InitiatingServerRecoveryState(
      val sealedCsek: SealedCsek,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    data class FailedInitiatingServerRecoveryState(
      val cause: Throwable,
      val sealedCsek: SealedCsek,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    sealed interface VerifyingNotificationCommsState : State {
      val sealedCsek: SealedCsek
      val destinationAppKeyBundle: AppKeyBundle
      val destinationHardwareKeyBundle: HwKeyBundle
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature

      data class VerifyingNotificationCommsForInitiationState(
        override val sealedCsek: SealedCsek,
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
        override val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      ) : VerifyingNotificationCommsState

      data class VerifyingNotificationCommsForCancellationState(
        override val sealedCsek: SealedCsek,
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
        override val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
        val hwFactorProofOfPossession: HwFactorProofOfPossession,
      ) : VerifyingNotificationCommsState
    }

    data class DisplayingConflictingRecoveryState(
      val sealedCsek: SealedCsek,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class CancellingConflictingRecoveryWithF8eState(
      val sealedCsek: SealedCsek,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class FailedToCancelConflictingRecoveryWithF8EState(
      val cause: Throwable,
      val sealedCsek: SealedCsek,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    data class AwaitingHardwareProofOfPossessionState(
      val sealedCsek: SealedCsek,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
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
