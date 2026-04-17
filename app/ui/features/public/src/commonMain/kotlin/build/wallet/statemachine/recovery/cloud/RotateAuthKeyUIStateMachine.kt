package build.wallet.statemachine.recovery.cloud

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.account.isW3Hardware
import bitkey.privilegedactions.ActionProofService
import bitkey.privilegedactions.ActionProofService.Companion.ACTION_PROOF_VERSION
import build.wallet.analytics.events.screen.context.AuthKeyRotationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.auth.AuthKeyRotationFailure
import build.wallet.auth.AuthKeyRotationRequest
import build.wallet.auth.FullAccountAuthKeyRotationService
import build.wallet.auth.PendingAuthKeyRotationAttempt
import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppRecoveryAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.crypto.PublicKey
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.PrivilegedActionProof
import build.wallet.keybox.keys.AppKeysGenerator
import build.wallet.logging.logDebug
import build.wallet.logging.logFailure
import build.wallet.logging.logWarn
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.RotateAppAuthKeysContinueParams
import build.wallet.nfc.transaction.ProvisionAppAuthKeyTransactionProvider
import build.wallet.platform.web.InAppBrowserNavigator
import build.wallet.statemachine.auth.ActionProofType
import build.wallet.statemachine.auth.ProofOfPossessionNfcProps
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachine
import build.wallet.statemachine.auth.RefreshAuthTokensProps
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachine
import build.wallet.statemachine.auth.Request
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorData
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.nfc.ConfirmationResultContent
import build.wallet.statemachine.nfc.NfcConfirmableSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcConfirmableSessionUiStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.recovery.RecoverySegment
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapBoth
import com.github.michaelbull.result.onFailure
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

@BitkeyInject(ActivityScope::class)
class RotateAuthKeyUIStateMachineImpl(
  val appKeysGenerator: AppKeysGenerator,
  val proofOfPossessionNfcStateMachine: ProofOfPossessionNfcStateMachine,
  val fullAccountAuthKeyRotationService: FullAccountAuthKeyRotationService,
  private val inAppBrowserNavigator: InAppBrowserNavigator,
  private val refreshAuthTokensUiStateMachine: RefreshAuthTokensUiStateMachine,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val actionProofService: ActionProofService,
  private val provisionAppAuthKeyTransactionProvider: ProvisionAppAuthKeyTransactionProvider,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
) : RotateAuthKeyUIStateMachine {
  @Composable
  override fun model(props: RotateAuthKeyUIStateMachineProps): ScreenModel {
    val eventTrackerScreenIdContext = remember(props.origin) {
      eventTrackerContext(props.origin)
    }

    var state: State by remember(props.origin) {
      mutableStateOf(initialState(props.origin))
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
      is State.WaitingOnChoiceState -> waitingOnChoice(props, uiState) {
        state = it
      }
      is State.ObtainingHwProofOfPossession -> waitingOnProofOfPossession(props, uiState) {
        state = it
      }

      // W3 action proof flow: refresh tokens → build payload → composite NFC tap
      is State.W3RefreshingTokens -> {
        refreshAuthTokensUiStateMachine.model(
          RefreshAuthTokensProps(
            fullAccountId = props.account.accountId,
            onSuccess = {
              state = State.W3BuildingPayload(
                appGlobalAndRecoveryAuthKeys = uiState.appGlobalAndRecoveryAuthKeys
              )
            },
            onBack = {
              state = State.WaitingOnChoiceState(
                appGlobalAndRecoveryAuthKeys = uiState.appGlobalAndRecoveryAuthKeys
              )
            },
            screenPresentationStyle = ScreenPresentationStyle.FullScreen
          )
        )
      }

      is State.W3BuildingPayload -> {
        LaunchedEffect("w3-build-and-sign-payload") {
          val type = ActionProofType.RotateAuthKeys
          actionProofService.buildAppSignedPayload(
            action = type.action,
            value = type.value,
            extra = type.extra,
            appAuthKey = props.account.keybox.activeAppKeyBundle.authKey,
            accountId = props.account.accountId
          )
            .logFailure { "Failed to build and app-sign action proof payload for auth key rotation" }
            .onSuccess { signed ->
              state = State.W3CompositeNfcTap(
                appGlobalAndRecoveryAuthKeys = uiState.appGlobalAndRecoveryAuthKeys,
                bindings = signed.bindings,
                appSignature = signed.appSignature,
                nonce = signed.nonce
              )
            }
            .onFailure {
              state = State.W3ActionProofError(
                appGlobalAndRecoveryAuthKeys = uiState.appGlobalAndRecoveryAuthKeys,
                error = it
              )
            }
        }

        LoadingBodyModel(
          id = AuthEventTrackerScreenId.ACTION_PROOF_BUILDING_PAYLOAD,
          title = "Loading..."
        ).asRootScreen()
      }

      is State.W3CompositeNfcTap -> {
        w3CompositeNfcTapModel(props, uiState) { state = it }
      }

      is State.W3ActionProofError -> {
        ErrorFormBodyModel(
          title = "We couldn't verify this action",
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = {
              state = State.W3BuildingPayload(
                appGlobalAndRecoveryAuthKeys = uiState.appGlobalAndRecoveryAuthKeys
              )
            }
          ),
          onBack = {
            state = State.WaitingOnChoiceState(
              appGlobalAndRecoveryAuthKeys = uiState.appGlobalAndRecoveryAuthKeys
            )
          },
          eventTrackerScreenId = AuthEventTrackerScreenId.ACTION_PROOF_ERROR,
          errorData = ErrorData(
            segment = RecoverySegment,
            actionDescription = "Sign out other devices via action proof",
            cause = uiState.error
          )
        ).asRootScreen()
      }

      is State.RotatingAuthKeys -> {
        LaunchedEffect("rotate auth keys") {
          state = getAuthKeyRotationResult(uiState, props)
        }

        RotateAuthKeyScreens.RotatingKeys(
          context = eventTrackerScreenIdContext
        ).asRootScreen()
      }
      is State.ProvisioningHardware -> nfcSessionUIStateMachine.model(
        props = NfcSessionUIStateMachineProps(
          transaction = provisionAppAuthKeyTransactionProvider(
            appGlobalAuthPublicKey = uiState.appGlobalAuthPublicKey,
            onSuccess = {
              state = State.AcknowledgingSuccess(onAcknowledge = uiState.onAcknowledge)
            },
            onCancel = {
              state = State.AcknowledgingSuccess(onAcknowledge = uiState.onAcknowledge)
            }
          ),
          screenPresentationStyle = ScreenPresentationStyle.FullScreen,
          eventTrackerContext = NfcEventTrackerScreenIdContext.ROTATE_AUTH_KEYS_PROVISION_APP_AUTH_KEY
        )
      )
      is State.AcknowledgingSuccess -> RotateAuthKeyScreens.Confirmation(
        context = eventTrackerScreenIdContext,
        onSelected = {
          uiState.onAcknowledge()
          if (props.origin is RotateAuthKeyUIOrigin.Settings) {
            props.origin.onBack()
          }
        }
      ).asRootScreen()
      is State.PresentingUnexpectedFailure -> RotateAuthKeyScreens.AccountOutOfSyncBodyModel(
        id = InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_UNEXPECTED,
        context = eventTrackerScreenIdContext,
        onRetry = {
          state = State.RotatingAuthKeys(uiState.retryRequest)
        },
        onContactSupport = {
          state = State.PresentingInAppBrowserCustomerSupportUi(uiState.retryRequest)
        },
        onClose = {
          when (val origin = props.origin) {
            is RotateAuthKeyUIOrigin.Settings -> origin.onBack()
            is RotateAuthKeyUIOrigin.PendingAttempt -> state = State.DismissingProposedAttempt
          }
        }
      ).asRootScreen()
      is State.PresentingRecoverableFailure -> RotateAuthKeyScreens.AcceptableFailure(
        context = eventTrackerScreenIdContext,
        onRetry = {
          val keys = AppGlobalAndRecoveryAuthKeys(
            globalKey = uiState.newAppAuthKeys.appGlobalAuthPublicKey,
            recoveryKey = uiState.newAppAuthKeys.appRecoveryAuthPublicKey
          )
          state = if (props.account.keybox.config.isW3Hardware) {
            State.W3RefreshingTokens(appGlobalAndRecoveryAuthKeys = keys)
          } else {
            State.ObtainingHwProofOfPossession(appGlobalAndRecoveryAuthKeys = keys)
          }
        },
        onAcknowledge = {
          uiState.onAcknowledge()
          if (props.origin is RotateAuthKeyUIOrigin.Settings) {
            props.origin.onBack()
          }
        }
      ).asRootScreen()
      is State.PresentingAccountLockedFailure -> RotateAuthKeyScreens.AccountOutOfSyncBodyModel(
        id = InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_ACCOUNT_LOCKED,
        context = eventTrackerScreenIdContext,
        onRetry = {
          state = State.RotatingAuthKeys(uiState.retryRequest)
        },
        onContactSupport = {
          state = State.PresentingInAppBrowserCustomerSupportUi(uiState.retryRequest)
        },
        onClose = {
          when (val origin = props.origin) {
            is RotateAuthKeyUIOrigin.Settings -> origin.onBack()
            is RotateAuthKeyUIOrigin.PendingAttempt -> state = State.DismissingProposedAttempt
          }
        }
      ).asRootScreen()
      State.DismissingProposedAttempt -> {
        LaunchedEffect("dismiss proposed attempt") {
          fullAccountAuthKeyRotationService.dismissProposedRotationAttempt()
        }

        RotateAuthKeyScreens.DismissingProposal(eventTrackerScreenIdContext).asRootScreen()
      }
    }
  }

  private fun eventTrackerContext(
    origin: RotateAuthKeyUIOrigin,
  ): AuthKeyRotationEventTrackerScreenIdContext =
    when (origin) {
      is RotateAuthKeyUIOrigin.PendingAttempt -> when (origin.attempt) {
        is PendingAuthKeyRotationAttempt.IncompleteAttempt -> AuthKeyRotationEventTrackerScreenIdContext.FAILED_ATTEMPT
        PendingAuthKeyRotationAttempt.ProposedAttempt -> AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }
      is RotateAuthKeyUIOrigin.Settings -> AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
    }

  private fun initialState(origin: RotateAuthKeyUIOrigin): State =
    when (origin) {
      is RotateAuthKeyUIOrigin.PendingAttempt -> when (origin.attempt) {
        PendingAuthKeyRotationAttempt.ProposedAttempt -> State.WaitingOnChoiceState(
          appGlobalAndRecoveryAuthKeys = null
        )
        is PendingAuthKeyRotationAttempt.IncompleteAttempt -> State.RotatingAuthKeys(
          request = AuthKeyRotationRequest.Resume(newKeys = origin.attempt.newKeys)
        )
      }
      is RotateAuthKeyUIOrigin.Settings -> State.WaitingOnChoiceState(appGlobalAndRecoveryAuthKeys = null)
    }

  private suspend fun getAuthKeyRotationResult(
    uiState: State.RotatingAuthKeys,
    props: RotateAuthKeyUIStateMachineProps,
  ) = fullAccountAuthKeyRotationService.startOrResumeAuthKeyRotation(
    request = uiState.request,
    account = props.account
  ).mapBoth(
    success = { success ->
      logDebug { "Successfully rotated auth keys" }
      State.ProvisioningHardware(
        appGlobalAuthPublicKey = uiState.request.newKeys.appGlobalAuthPublicKey,
        onAcknowledge = success.onAcknowledge
      )
    },
    failure = { failure ->
      logWarn { "Failed to rotate auth keys" }
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

  private suspend fun generateAppAuthKeys(): Result<AppGlobalAndRecoveryAuthKeys, Throwable> {
    return coroutineBinding {
      with(appKeysGenerator) {
        val appGlobalAuthPublicKey = generateGlobalAuthKey().bind()
        val appRecoveryAuthPublicKey = generateRecoveryAuthKey().bind()
        AppGlobalAndRecoveryAuthKeys(
          globalKey = appGlobalAuthPublicKey,
          recoveryKey = appRecoveryAuthPublicKey
        )
      }
    }.logFailure { "Error generating new app auth keys" }
  }

  @Composable
  private fun waitingOnChoice(
    props: RotateAuthKeyUIStateMachineProps,
    state: State.WaitingOnChoiceState,
    setState: (State) -> Unit,
  ): ScreenModel {
    if (state.appGlobalAndRecoveryAuthKeys == null) {
      LaunchedEffect("generate-new-app-auth-keys") {
        // Since we are rotating app global auth key, we need to create
        // a new AppGlobalAuthKeyHwSignature as well by tapping hardware.
        // This requires having a new app global auth key before the hardware tap,
        // so we are preloading it here, to save us from an extra tap after auth keys are rotated.
        generateAppAuthKeys()
          .onSuccess { keys ->
            setState(State.WaitingOnChoiceState(appGlobalAndRecoveryAuthKeys = keys))
          }
      }
    }

    val isW3 = props.account.keybox.config.isW3Hardware

    val removeAllOtherDevices = remember(state.appGlobalAndRecoveryAuthKeys, isW3) {
      if (state.appGlobalAndRecoveryAuthKeys == null) {
        { /* noop */ }
      } else if (isW3) {
        {
          setState(
            State.W3RefreshingTokens(
              appGlobalAndRecoveryAuthKeys = state.appGlobalAndRecoveryAuthKeys
            )
          )
        }
      } else {
        {
          setState(
            State.ObtainingHwProofOfPossession(
              appGlobalAndRecoveryAuthKeys = state.appGlobalAndRecoveryAuthKeys
            )
          )
        }
      }
    }

    return when (val origin = props.origin) {
      is RotateAuthKeyUIOrigin.PendingAttempt -> RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice(
        onNotRightNow = { setState(State.DismissingProposedAttempt) },
        removeAllOtherDevicesEnabled = state.appGlobalAndRecoveryAuthKeys != null,
        onRemoveAllOtherDevices = removeAllOtherDevices
      )
      is RotateAuthKeyUIOrigin.Settings -> RotateAuthKeyScreens.DeactivateDevicesFromSettingsChoice(
        onBack = origin.onBack,
        removeAllOtherDevicesEnabled = state.appGlobalAndRecoveryAuthKeys != null,
        onRemoveAllOtherDevices = removeAllOtherDevices
      )
    }.asRootScreen()
  }

  @Composable
  private fun waitingOnProofOfPossession(
    props: RotateAuthKeyUIStateMachineProps,
    state: State.ObtainingHwProofOfPossession,
    setState: (State) -> Unit,
  ) = proofOfPossessionNfcStateMachine.model(
    props = ProofOfPossessionNfcProps(
      request = Request.HwKeyProofAndAccountSignature(
        appAuthGlobalKey = state.appGlobalAndRecoveryAuthKeys.globalKey,
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
                newKeys = AppAuthPublicKeys(
                  appGlobalAuthPublicKey = state.appGlobalAndRecoveryAuthKeys.globalKey,
                  appRecoveryAuthPublicKey = state.appGlobalAndRecoveryAuthKeys.recoveryKey,
                  appGlobalAuthKeyHwSignature = appGlobalAuthKeyHwSignature
                ),
                proof = PrivilegedActionProof.HwKeyProof(hwFactorProofOfPossession),
                hwAuthPublicKey = hwAuthPublicKey,
                hwSignedAccountId = signedAccountId
              )
            )
          )
        }
      ),
      fullAccountId = props.account.keybox.fullAccountId,
      screenPresentationStyle = ScreenPresentationStyle.FullScreen,
      appAuthKey = props.account.keybox.activeAppKeyBundle.authKey,
      onBack = {
        setState(State.WaitingOnChoiceState(appGlobalAndRecoveryAuthKeys = state.appGlobalAndRecoveryAuthKeys))
      }
    )
  )

  /**
   * W3 composite NFC tap: calls [NfcCommands.rotateAppAuthKeys] which signs the action proof,
   * the new app global auth key, and the account ID in a single confirmable tap.
   */
  @Composable
  private fun w3CompositeNfcTapModel(
    props: RotateAuthKeyUIStateMachineProps,
    state: State.W3CompositeNfcTap,
    setState: (State) -> Unit,
  ): ScreenModel {
    return nfcConfirmableSessionUiStateMachine.model(
      NfcConfirmableSessionUIStateMachineProps(
        session = { session, commands ->
          commands.rotateAppAuthKeys(
            session = session,
            params = RotateAppAuthKeysContinueParams(
              actionProofVersion = ACTION_PROOF_VERSION,
              actionProofAction = ActionProofAction.ROTATE_APP_AUTH_KEYS,
              actionProofBindings = state.bindings,
              accountId = props.account.accountId.serverId,
              appGlobalAuthPublicKey = state.appGlobalAndRecoveryAuthKeys.globalKey.value
            )
          )
        },
        onSuccess = { result ->
          // HW returns compact (r||s) hex-encoded action proof signature.
          val hwSignature = result.actionProofSignature.lowercase()

          actionProofService.createActionProofHeader(
            signatures = listOf(state.appSignature, hwSignature),
            nonce = state.nonce
          )
            .logFailure { "Failed to create action proof header for auth key rotation" }
            .onSuccess { header ->
              setState(
                State.RotatingAuthKeys(
                  request = AuthKeyRotationRequest.Start(
                    newKeys = AppAuthPublicKeys(
                      appGlobalAuthPublicKey = state.appGlobalAndRecoveryAuthKeys.globalKey,
                      appRecoveryAuthPublicKey = state.appGlobalAndRecoveryAuthKeys.recoveryKey,
                      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignature(
                        result.appGlobalAuthKeyHwSignature
                      )
                    ),
                    proof = PrivilegedActionProof.HwSignedAction(actionProof = header),
                    hwAuthPublicKey = props.account.keybox.activeHwKeyBundle.authKey,
                    hwSignedAccountId = result.hwSignedAccountId
                  )
                )
              )
            }
            .onFailure {
              setState(
                State.W3ActionProofError(
                  appGlobalAndRecoveryAuthKeys = state.appGlobalAndRecoveryAuthKeys,
                  error = it
                )
              )
            }
        },
        onCancel = {
          setState(
            State.WaitingOnChoiceState(
              appGlobalAndRecoveryAuthKeys = state.appGlobalAndRecoveryAuthKeys
            )
          )
        },
        segment = RecoverySegment,
        actionDescription = "Sign out other devices via action proof",
        screenPresentationStyle = ScreenPresentationStyle.FullScreen,
        eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_ACTION_PROOF,
        confirmationContent = HardwareConfirmationContent.SignActionProof,
        confirmationResultContent = ConfirmationResultContent(
          pendingHeadline = "Review action on Bitkey",
          pendingSubline = "You’ll need to approve or deny on your Bitkey device before tapping again."
        ),
        hardwareVerification = Required(),
        hardwareTypeOverride = HardwareType.W3
      )
    )
  }

  private sealed interface State {
    // We're waiting on the customer to choose an option.
    data class WaitingOnChoiceState(
      val appGlobalAndRecoveryAuthKeys: AppGlobalAndRecoveryAuthKeys?,
    ) : State

    // W1 path: obtaining HW proof of possession via NFC
    data class ObtainingHwProofOfPossession(
      val appGlobalAndRecoveryAuthKeys: AppGlobalAndRecoveryAuthKeys,
    ) : State

    // W3 path step 1: refreshing auth tokens before building action proof
    data class W3RefreshingTokens(
      val appGlobalAndRecoveryAuthKeys: AppGlobalAndRecoveryAuthKeys,
    ) : State

    // W3 path step 2: building action proof payload, bindings, and app-signing
    data class W3BuildingPayload(
      val appGlobalAndRecoveryAuthKeys: AppGlobalAndRecoveryAuthKeys,
    ) : State

    // W3 path step 3: composite NFC tap that signs action proof + app auth key + account ID
    data class W3CompositeNfcTap(
      val appGlobalAndRecoveryAuthKeys: AppGlobalAndRecoveryAuthKeys,
      val bindings: String,
      val appSignature: String,
      val nonce: String,
    ) : State

    // W3 path: error during action proof building or header creation
    data class W3ActionProofError(
      val appGlobalAndRecoveryAuthKeys: AppGlobalAndRecoveryAuthKeys,
      val error: Throwable,
    ) : State

    data class RotatingAuthKeys(val request: AuthKeyRotationRequest) : State

    data class ProvisioningHardware(
      val appGlobalAuthPublicKey: PublicKey<AppGlobalAuthKey>,
      val onAcknowledge: () -> Unit,
    ) : State

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

/**
 * Holds the newly generated app global and recovery auth keys. Primarily is used as a
 * transient state to pass the newly generated keys until the global auth key is signed with
 * hardware and [AppAuthPublicKeys] can be constructed.
 */
private data class AppGlobalAndRecoveryAuthKeys(
  val globalKey: PublicKey<AppGlobalAuthKey>,
  val recoveryKey: PublicKey<AppRecoveryAuthKey>,
)
