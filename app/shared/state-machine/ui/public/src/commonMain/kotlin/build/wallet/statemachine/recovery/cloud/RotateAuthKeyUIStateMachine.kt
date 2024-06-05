package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.context.AuthKeyRotationEventTrackerScreenIdContext
import build.wallet.auth.AuthKeyRotationFailure
import build.wallet.auth.AuthKeyRotationManager
import build.wallet.auth.AuthKeyRotationRequest
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.logging.logFailure
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onSuccess

interface RotateAuthKeyUIStateMachine :
  StateMachine<RotateAuthKeyUIStateMachineProps, ScreenModel>

data class RotateAuthKeyUIStateMachineProps(
  val account: FullAccount,
  val origin: RotateAuthKeyUIOrigin,
)

/**
 * The rotation can happen from three places (origins):
 * 1. when recovering from the cloud,
 * 2. going in through the settings,
 * 3. or on app startup when a previous rotation attempt has failed.
 *
 * When recovering from the cloud,
 * we want to give users the option to skip this step,
 * with a dedicated button next to the one that rotates keys.
 *
 * However, when users are going through settings,
 * they expect to have a back button in the toolbar,
 * so we only show the one button to rotate keys on the bottom.
 *
 * When a previous rotation attempt has failed,
 * we go directly into a loading screen trying to recover the previous attempt.
 */
sealed interface RotateAuthKeyUIOrigin {
  data class PendingAttempt(
    val attempt: PendingAuthKeyRotationAttempt,
  ) : RotateAuthKeyUIOrigin

  data class Settings(val onBack: () -> Unit) : RotateAuthKeyUIOrigin
}

class RotateAuthKeyUIStateMachineImpl(
  val appKeysGenerator: AppKeysGenerator,
  val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  val authKeyRotationManager: AuthKeyRotationManager,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
) : RotateAuthKeyUIStateMachine {
  @Composable
  override fun model(props: RotateAuthKeyUIStateMachineProps): ScreenModel {
    val eventTrackerScreenIdContext = remember(props.origin) {
      when (props.origin) {
        is RotateAuthKeyUIOrigin.PendingAttempt -> when (props.origin.attempt) {
          is PendingAuthKeyRotationAttempt.IncompleteAttempt -> AuthKeyRotationEventTrackerScreenIdContext.FAILED_ATTEMPT
          PendingAuthKeyRotationAttempt.ProposedAttempt -> AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        }
        is RotateAuthKeyUIOrigin.Settings -> AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }
    }

    var state: State by remember(props.origin) {
      val initialState = when (props.origin) {
        is RotateAuthKeyUIOrigin.PendingAttempt -> when (props.origin.attempt) {
          PendingAuthKeyRotationAttempt.ProposedAttempt -> State.WaitingOnChoiceState(newAppAuthKeys = null)
          is PendingAuthKeyRotationAttempt.IncompleteAttempt -> State.RotatingAuthKeys(
            request = AuthKeyRotationRequest.Resume(newKeys = props.origin.attempt.newKeys)
          )
        }
        is RotateAuthKeyUIOrigin.Settings -> State.WaitingOnChoiceState(newAppAuthKeys = null)
      }
      mutableStateOf(initialState)
    }

    return when (val uiState = state) {
      is State.PresentingInAppBrowserCustomerSupportUi -> {
        InAppBrowserModel(
          open = {
            inAppBrowserNavigator.open(
              url = "https://support.bitkey.world/hc/en-us",
              onClose = { state = State.RotatingAuthKeys(uiState.retryRequest) }
            )
          }
        ).asModalScreen()
      }
      is State.WaitingOnChoiceState -> {
        if (uiState.newAppAuthKeys == null) {
          LaunchedEffect("generate-new-app-auth-keys") {
            // Since we are rotating app global auth key, we need to create
            // a new AppGlobalAuthKeyHwSignature as well by tapping hardware.
            // This requires having a new app global auth key before the hardware tap,
            // so we are preloading it here, to save us from an extra tap after auth keys are rotated.
            generateAppAuthKeys()
              .onSuccess {
                state = State.WaitingOnChoiceState(newAppAuthKeys = it)
              }
          }
        }

        val removeAllOtherDevices = remember(uiState.newAppAuthKeys) {
          if (uiState.newAppAuthKeys == null) {
            { /* noop */ }
          } else {
            { state = State.ObtainingHwProofOfPossession(uiState.newAppAuthKeys) }
          }
        }

        when (val origin = props.origin) {
          is RotateAuthKeyUIOrigin.PendingAttempt -> RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice(
            onNotRightNow = { state = State.DismissingProposedAttempt },
            removeAllOtherDevicesEnabled = uiState.newAppAuthKeys != null,
            onRemoveAllOtherDevices = removeAllOtherDevices
          )
          is RotateAuthKeyUIOrigin.Settings -> RotateAuthKeyScreens.DeactivateDevicesFromSettingsChoice(
            onBack = origin.onBack,
            removeAllOtherDevicesEnabled = uiState.newAppAuthKeys != null,
            onRemoveAllOtherDevices = removeAllOtherDevices
          )
        }.asRootScreen()
      }
      is State.ObtainingHwProofOfPossession -> waitingOnProofOfPossession(props, uiState) {
        state = it
      }
      is State.RotatingAuthKeys -> {
        LaunchedEffect("rotate auth keys") {
          state = getAuthKeyRotationResult(uiState, props)
        }

        RotateAuthKeyScreens.RotatingKeys(
          context = eventTrackerScreenIdContext
        ).asRootScreen()
      }
      is State.AcknowledgingSuccess -> RotateAuthKeyScreens.Confirmation(
        context = eventTrackerScreenIdContext,
        onSelected = {
          uiState.onAcknowledge()
          if (props.origin is RotateAuthKeyUIOrigin.Settings) {
            props.origin.onBack()
          }
        }
      ).asRootScreen()
      is State.PresentingUnexpectedFailure -> RotateAuthKeyScreens.UnexpectedFailure(
        context = eventTrackerScreenIdContext,
        onRetry = {
          state = State.RotatingAuthKeys(uiState.retryRequest)
        },
        onContactSupport = {
          state = State.PresentingInAppBrowserCustomerSupportUi(uiState.retryRequest)
        }
      ).asRootScreen()
      is State.PresentingRecoverableFailure -> RotateAuthKeyScreens.AcceptableFailure(
        context = eventTrackerScreenIdContext,
        onRetry = { state = State.ObtainingHwProofOfPossession(uiState.newAppAuthKeys) },
        onAcknowledge = {
          uiState.onAcknowledge()
          if (props.origin is RotateAuthKeyUIOrigin.Settings) {
            props.origin.onBack()
          }
        }
      ).asRootScreen()
      is State.PresentingAccountLockedFailure -> RotateAuthKeyScreens.AccountLockedFailure(
        context = eventTrackerScreenIdContext,
        onRetry = {
          state = State.RotatingAuthKeys(uiState.retryRequest)
        },
        onContactSupport = {
          state = State.PresentingInAppBrowserCustomerSupportUi(uiState.retryRequest)
        }
      ).asRootScreen()
      State.DismissingProposedAttempt -> {
        LaunchedEffect("dismiss proposed attempt") {
          authKeyRotationManager.dismissProposedRotationAttempt()
        }

        RotateAuthKeyScreens.DismissingProposal(eventTrackerScreenIdContext).asRootScreen()
      }
    }
  }

  private suspend fun getAuthKeyRotationResult(
    uiState: State.RotatingAuthKeys,
    props: RotateAuthKeyUIStateMachineProps,
  ) = authKeyRotationManager.startOrResumeAuthKeyRotation(
    request = uiState.request,
    account = props.account
  ).mapBoth(
    success = { success ->
      log(LogLevel.Debug) { "Successfully rotated auth keys" }
      State.AcknowledgingSuccess(
        onAcknowledge = success.onAcknowledge
      )
    },
    failure = { failure ->
      log(LogLevel.Warn) { "Failed to rotate auth keys" }
      when (failure) {
        is AuthKeyRotationFailure.Acceptable -> State.PresentingRecoverableFailure(
          newAppAuthKeys = uiState.request.newKeys,
          onAcknowledge = failure.onAcknowledge
        )
        is AuthKeyRotationFailure.Unexpected -> State.PresentingUnexpectedFailure(
          retryRequest = failure.retryRequest
        )
        is AuthKeyRotationFailure.AccountLocked -> State.PresentingAccountLockedFailure(
          retryRequest = failure.retryRequest
        )
      }
    }
  )

  private suspend fun generateAppAuthKeys(): Result<AppAuthPublicKeys, Throwable> =
    binding {
      with(appKeysGenerator) {
        val appGlobalAuthPublicKey = generateGlobalAuthKey().bind()
        val appRecoveryAuthPublicKey = generateRecoveryAuthKey().bind()
        AppAuthPublicKeys(
          appGlobalAuthPublicKey,
          appRecoveryAuthPublicKey,
          appGlobalAuthKeyHwSignature = null
        )
      }
    }.logFailure { "Error generating new app auth keys" }

  @Composable
  private fun waitingOnProofOfPossession(
    props: RotateAuthKeyUIStateMachineProps,
    state: State.ObtainingHwProofOfPossession,
    setState: (State) -> Unit,
  ) = proofOfPossessionNfcStateMachine.model(
    props = ProofOfPossessionNfcProps(
      request = Request.HwKeyProofAndAccountSignature(
        appAuthGlobalKey = state.newAppAuthKeys.appGlobalAuthPublicKey,
        accountId = props.account.keybox.fullAccountId,
        onSuccess = {
            signedAccountId,
            hwAuthPublicKey,
            hwFactorProofOfPossession,
            appGlobalAuthKeyHwSignature,
          ->
          setState(
            State.RotatingAuthKeys(
              request = AuthKeyRotationRequest.Start(
                newKeys = state.newAppAuthKeys.copy(
                  appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
                ),
                hwFactorProofOfPossession = hwFactorProofOfPossession,
                hwAuthPublicKey = hwAuthPublicKey,
                hwSignedAccountId = signedAccountId
              )
            )
          )
        }
      ),
      fullAccountId = props.account.keybox.fullAccountId,
      fullAccountConfig = props.account.keybox.config,
      screenPresentationStyle = ScreenPresentationStyle.FullScreen,
      appAuthKey = props.account.keybox.activeAppKeyBundle.authKey,
      onBack = { setState(State.WaitingOnChoiceState(newAppAuthKeys = state.newAppAuthKeys)) }
    )
  )

  private sealed interface State {
    // We're waiting on the customer to choose an option.
    data class WaitingOnChoiceState(
      val newAppAuthKeys: AppAuthPublicKeys?,
    ) : State

    // We've passed control to the proof of possession state machine and are awaiting it's completion
    data class ObtainingHwProofOfPossession(
      val newAppAuthKeys: AppAuthPublicKeys,
    ) : State

    data class RotatingAuthKeys(val request: AuthKeyRotationRequest) : State

    data class AcknowledgingSuccess(
      val onAcknowledge: () -> Unit,
    ) : State

    data class PresentingUnexpectedFailure(
      val retryRequest: AuthKeyRotationRequest,
    ) : State

    data class PresentingRecoverableFailure(
      val newAppAuthKeys: AppAuthPublicKeys,
      val onAcknowledge: () -> Unit,
    ) : State

    data class PresentingAccountLockedFailure(
      val retryRequest: AuthKeyRotationRequest,
    ) : State

    data object DismissingProposedAttempt : State

    data class PresentingInAppBrowserCustomerSupportUi(
      val retryRequest: AuthKeyRotationRequest,
    ) : State
  }
}
