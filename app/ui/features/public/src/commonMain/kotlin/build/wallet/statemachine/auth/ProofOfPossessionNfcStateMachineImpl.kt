package build.wallet.statemachine.auth

import androidx.compose.runtime.*
import bitkey.auth.AccessToken
import build.wallet.analytics.events.screen.context.NfcEventTrackerScreenIdContext
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.f8e.auth.HwFactorProofOfPossession
import build.wallet.nfc.platform.signAccessToken
import build.wallet.nfc.transaction.SignAccountIdAndAuthData
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachineImpl.State.RefreshingAuthTokensState
import build.wallet.statemachine.auth.ProofOfPossessionNfcStateMachineImpl.State.ShowingNfcState
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.nfc.NfcSessionUIStateMachine
import build.wallet.statemachine.nfc.NfcSessionUIStateMachineProps

@BitkeyInject(ActivityScope::class)
class ProofOfPossessionNfcStateMachineImpl(
  private val nfcSessionUIStateMachine: NfcSessionUIStateMachine,
  private val refreshAuthTokensUiStateMachine: RefreshAuthTokensUiStateMachine,
) : ProofOfPossessionNfcStateMachine {
  @Composable
  override fun model(props: ProofOfPossessionNfcProps): ScreenModel {
    var uiState: State by remember {
      val initialState =
        when (props.authTokens) {
          null -> RefreshingAuthTokensState
          else -> ShowingNfcState(props.authTokens.accessToken)
        }
      mutableStateOf(initialState)
    }

    return when (val state = uiState) {
      RefreshingAuthTokensState ->
        refreshAuthTokensUiStateMachine.model(
          RefreshAuthTokensProps(
            fullAccountId = props.fullAccountId,
            appAuthKey = props.appAuthKey,
            onSuccess = { uiState = ShowingNfcState(it.accessToken) },
            onBack = props.onBack,
            onTokenRefresh = props.onTokenRefresh,
            onTokenRefreshError = props.onTokenRefreshError,
            screenPresentationStyle = props.screenPresentationStyle
          )
        )

      is ShowingNfcState -> hwPopNfcStateMachine(state.accessToken, props)
    }
  }

  private sealed interface State {
    data object RefreshingAuthTokensState : State

    data class ShowingNfcState(val accessToken: AccessToken) : State
  }

  @Composable
  private fun hwPopNfcStateMachine(
    accessToken: AccessToken,
    props: ProofOfPossessionNfcProps,
  ): ScreenModel {
    val nfcStateMachineProps =
      when (val request = props.request) {
        is Request.HwKeyProof ->
          NfcSessionUIStateMachineProps(
            session = { session, commands -> commands.signAccessToken(session, accessToken) },
            onSuccess = { request.onSuccess(HwFactorProofOfPossession(it)) },
            onCancel = props.onBack,
            segment = props.segment,
            actionDescription = props.actionDescription,
            screenPresentationStyle = props.screenPresentationStyle,
            hardwareVerification = props.hardwareVerification,
            shouldLock = props.shouldLock,
            eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
          )

        is Request.HwKeyProofAndAccountSignature -> {
          val nfcTransaction =
            SignAccountIdAndAuthData(
              appAuthGlobalAuthPublicKey = request.appAuthGlobalKey,
              accessToken = accessToken,
              fullAccountId = request.accountId,
              success = { signedAccessTokensAndAccountId ->
                request.onSuccess(
                  signedAccessTokensAndAccountId.signedAccountId,
                  signedAccessTokensAndAccountId.hwAuthPublicKey,
                  HwFactorProofOfPossession(signedAccessTokensAndAccountId.signedAccessToken),
                  signedAccessTokensAndAccountId.appGlobalAuthKeyHwSignature
                )
              },
              failure = props.onBack,
              needsAuthentication = true,
              shouldLock = false
            )

          NfcSessionUIStateMachineProps(
            session = nfcTransaction::session,
            onSuccess = nfcTransaction::onSuccess,
            onCancel = nfcTransaction::onCancel,
            screenPresentationStyle = props.screenPresentationStyle,
            hardwareVerification = props.hardwareVerification,
            eventTrackerContext = NfcEventTrackerScreenIdContext.HW_PROOF_OF_POSSESSION
          )
        }
      }

    return nfcSessionUIStateMachine.model(nfcStateMachineProps)
  }
}
