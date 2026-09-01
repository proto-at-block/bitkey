package build.wallet.statemachine.auth

import androidx.compose.runtime.*
import bitkey.account.HardwareType
import bitkey.auth.AccessToken
import bitkey.privilegedactions.ActionProofService
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.f8e.auth.PrivilegedActionProof.HwKeyProof
import build.wallet.f8e.auth.PrivilegedActionProof.HwSignedAction
import build.wallet.logging.logFailure
import build.wallet.nfc.platform.ActionProofAction
import build.wallet.nfc.platform.requireW3
import build.wallet.nfc.platform.signAccessToken
import build.wallet.statemachine.core.*
import build.wallet.statemachine.nfc.*
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps.HardwareVerification.Required
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationContent
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory.Companion.CloseAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

@BitkeyInject(ActivityScope::class)
class HardwareAuthUiStateMachineImpl(
  private val refreshAuthTokensUiStateMachine: RefreshAuthTokensUiStateMachine,
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val nfcConfirmableSessionUiStateMachine: NfcConfirmableSessionUiStateMachine,
  private val actionProofService: ActionProofService,
) : HardwareAuthUiStateMachine {
  @Composable
  override fun model(props: HardwareAuthUiProps): ScreenModel {
    var state: State by remember(
      props.fullAccountId,
      props.hardwareType,
      props.actionProofType,
      props.refreshAuthTokens,
      props.authTokens
    ) {
      mutableStateOf(
        initialState(props)
      )
    }

    return when (val currentState = state) {
      is State.RefreshingAuthTokens ->
        refreshAuthTokensUiStateMachine.model(
          RefreshAuthTokensProps(
            fullAccountId = props.fullAccountId,
            onSuccess = { tokens ->
              state = when (props.hardwareType) {
                HardwareType.W1 -> State.W1SigningWithNfc(tokens.accessToken)
                HardwareType.W3 -> State.W3BuildingAndAppSigning
              }
            },
            onBack = props.onBack,
            onTokenRefresh = props.onTokenRefresh,
            onTokenRefreshError = props.onTokenRefreshError,
            screenPresentationStyle = props.screenPresentationStyle
          )
        )

      is State.W1SigningWithNfc ->
        nfcSessionUIStateMachine.model(
          NfcSessionUIStateMachineProps(
            session = { session, commands ->
              commands.signAccessToken(session, currentState.accessToken)
            },
            onSuccess = { signedToken ->
              props.onSuccess(
                HwKeyProof(
                  HwFactorProofOfPossession(
                    hwSignedToken = signedToken,
                    accessToken = currentState.accessToken.raw
                  )
                )
              )
            },
            onCancel = props.onBack,
            segment = props.segment,
            actionDescription = props.actionDescription,
            screenPresentationStyle = props.screenPresentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION,
            hardwareVerification = Required(useRecoveryPubKey = props.useRecoveryPubKey),
            hardwareTypeOverride = props.hardwareType
          )
        )

      is State.W3BuildingAndAppSigning -> {
        LaunchedEffect("build-and-app-sign-payload") {
          val type = props.actionProofType
          if (type.hwSignatureOnly) {
            val nonce = actionProofService.generateNonce()
            actionProofService.buildBindings(
              extra = type.extra,
              nonce = nonce,
              accountId = props.fullAccountId
            )
              .logFailure { "Failed to build bindings for hw-only action proof" }
              .onSuccess { bindings ->
                state = State.W3SigningWithNfc(
                  bindings = bindings,
                  appSignature = null,
                  nonce = nonce
                )
              }
              .onFailure { state = State.ActionProofError(it) }
          } else {
            actionProofService.buildAppSignedPayload(
              action = type.action,
              value = type.value,
              extra = type.extra,
              appAuthKey = props.appAuthKey,
              accountId = props.fullAccountId
            )
              .logFailure { "Failed to build and app-sign action proof payload" }
              .onSuccess { signed ->
                state = State.W3SigningWithNfc(
                  bindings = signed.bindings,
                  appSignature = signed.appSignature,
                  nonce = signed.nonce
                )
              }
              .onFailure { state = State.ActionProofError(it) }
          }
        }

        LoadingBodyModel(
          id = AuthEventTrackerScreenId.ACTION_PROOF_BUILDING_PAYLOAD,
          title = "Loading..."
        ).asScreen(props.screenPresentationStyle)
      }

      is State.W3SigningWithNfc -> {
        val type = props.actionProofType
        nfcConfirmableSessionUiStateMachine.model(
          NfcConfirmableSessionUIStateMachineProps(
            session = { session, commands ->
              commands.requireW3(session).signActionProof(
                session = session,
                version = 1u,
                action = ActionProofAction.from(type.action),
                value = type.value,
                bindings = currentState.bindings
              )
            },
            onSuccess = { hwSignature ->
              val signatures = listOfNotNull(currentState.appSignature, hwSignature)
              actionProofService.createActionProofHeader(
                signatures = signatures,
                nonce = currentState.nonce
              )
                .logFailure { "Failed to create action proof header" }
                .onSuccess { header ->
                  props.onSuccess(HwSignedAction(actionProof = header))
                }
                .onFailure { state = State.ActionProofError(it) }
            },
            onCancel = props.onBack,
            segment = props.segment,
            actionDescription = props.actionDescription,
            screenPresentationStyle = props.screenPresentationStyle,
            eventTrackerContext = NfcEventTrackerScreenIdContext.SIGN_ACTION_PROOF,
            confirmationContent = HardwareConfirmationContent.SignActionProof,
            confirmationResultContent = ConfirmationResultContent(
              pendingHeadline = "Review action on Bitkey",
              pendingSubline = "You’ll need to approve or deny on your Bitkey device before tapping again."
            ),
            hardwareVerification = Required(useRecoveryPubKey = props.useRecoveryPubKey),
            hardwareTypeOverride = props.hardwareType,
            shouldLock = props.shouldLock,
            showDeviceConfirmation = true
          )
        )
      }

      is State.ActionProofError -> {
        ErrorFormBodyModel(
          title = "We couldn’t verify this action",
          primaryButton = ButtonDataModel(
            text = "Retry",
            onClick = { state = initialState(props) }
          ),
          onBack = props.onBack,
          toolbar = ToolbarModel(
            leadingAccessory = CloseAccessory(onClick = props.onBack)
          ),
          eventTrackerScreenId = AuthEventTrackerScreenId.ACTION_PROOF_ERROR,
          errorData = ErrorData(
            segment = props.segment,
            actionDescription = props.actionDescription,
            cause = currentState.error
          )
        ).asScreen(props.screenPresentationStyle)
      }
    }
  }

  private fun initialState(props: HardwareAuthUiProps): State =
    when {
      props.hardwareType == HardwareType.W1 &&
        props.actionProofType.hwSignatureOnly ->
        props.authTokens
          ?.let { State.W1SigningWithNfc(it.accessToken) }
          ?: State.ActionProofError(
            IllegalStateException(
              "Missing auth tokens for W1 hardware-signature-only proof"
            )
          )

      props.hardwareType == HardwareType.W3 &&
        (!props.refreshAuthTokens || props.actionProofType.hwSignatureOnly) ->
        State.W3BuildingAndAppSigning

      else -> State.RefreshingAuthTokens
    }

  private sealed interface State {
    /** Refreshes auth tokens before flows that need a fresh app-authenticated token. */
    data object RefreshingAuthTokens : State

    // W1 states
    data class W1SigningWithNfc(val accessToken: AccessToken) : State

    // W3 states
    data object W3BuildingAndAppSigning : State

    data class W3SigningWithNfc(
      val bindings: String,
      val appSignature: String?,
      val nonce: String,
    ) : State

    // Shared error state
    data class ActionProofError(val error: Throwable) : State
  }
}
