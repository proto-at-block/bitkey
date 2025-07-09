package build.wallet.statemachine.data.recovery.losthardware.initiate

import androidx.compose.runtime.*
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.code.CancelDelayNotifyRecoveryErrorCode
import bitkey.recovery.InitiateDelayNotifyRecoveryError.*
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppKeyBundle
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwKeyBundle
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.recovery.CancelDelayNotifyRecoveryF8eClient
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.recovery.LostHardwareRecoveryService
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
  private val lostHardwareRecoveryService: LostHardwareRecoveryService,
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
              state = ErrorGeneratingNewAppKeysState(it)
            }
        }
        GeneratingNewAppKeysData
      }

      is ErrorGeneratingNewAppKeysState ->
        ErrorGeneratingNewAppKeysData(
          retry = { state = GeneratingNewAppKeys },
          cause = s.cause
        )

      is OnboardingNewHardware ->
        AwaitingNewHardwareData(
          newAppGlobalAuthKey = s.newAppKeys.authKey,
          addHardwareKeys = { keyBundle, appGlobalAuthKeyHwSignature ->
            state = InitiatingServerRecoveryState(
              destinationAppKeyBundle = s.newAppKeys,
              destinationHardwareKeyBundle = keyBundle,
              appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
            )
          }
        )

      is InitiatingServerRecoveryState -> {
        LaunchedEffect("initiating-lost-hardware-server-recovery") {
          lostHardwareRecoveryService
            .initiate(
              destinationAppKeyBundle = s.destinationAppKeyBundle,
              destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
              appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
            )
            .onFailure {
              state = when (it) {
                is CommsVerificationRequiredError ->
                  VerifyingNotificationCommsForInitiationState(
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
                is RecoveryAlreadyExistsError ->
                  DisplayingConflictingRecoveryState(
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
                is OtherError ->
                  FailedInitiatingServerRecoveryState(
                    cause = it,
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
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    hwFactorProofOfPossession = s.hwFactorProofOfPossession,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
                }

                is VerifyingNotificationCommsForInitiationState -> {
                  InitiatingServerRecoveryState(
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
                    destinationAppKeyBundle = s.destinationAppKeyBundle,
                    destinationHardwareKeyBundle = s.destinationHardwareKeyBundle,
                    hwFactorProofOfPossession = s.hwFactorProofOfPossession,
                    appGlobalAuthKeyHwSignature = s.appGlobalAuthKeyHwSignature
                  )
                } else {
                  FailedToCancelConflictingRecoveryWithF8EState(
                    cause = f8eError.error,
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
        FailedToCancelConflictingRecoveryData(
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
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    data class FailedInitiatingServerRecoveryState(
      val cause: Throwable,
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    sealed interface VerifyingNotificationCommsState : State {
      val destinationAppKeyBundle: AppKeyBundle
      val destinationHardwareKeyBundle: HwKeyBundle
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature

      data class VerifyingNotificationCommsForInitiationState(
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
        override val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
      ) : VerifyingNotificationCommsState

      data class VerifyingNotificationCommsForCancellationState(
        override val destinationAppKeyBundle: AppKeyBundle,
        override val destinationHardwareKeyBundle: HwKeyBundle,
        override val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
        val hwFactorProofOfPossession: HwFactorProofOfPossession,
      ) : VerifyingNotificationCommsState
    }

    data class DisplayingConflictingRecoveryState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    /**
     * Corresponds to [CancellingConflictingRecoveryData].
     */
    data class CancellingConflictingRecoveryWithF8eState(
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
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val hwFactorProofOfPossession: HwFactorProofOfPossession,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State

    data class AwaitingHardwareProofOfPossessionState(
      val destinationAppKeyBundle: AppKeyBundle,
      val destinationHardwareKeyBundle: HwKeyBundle,
      val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
    ) : State
  }
}
