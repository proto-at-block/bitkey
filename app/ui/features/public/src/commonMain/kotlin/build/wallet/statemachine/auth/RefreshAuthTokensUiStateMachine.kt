package build.wallet.statemachine.auth

import androidx.compose.runtime.*
import bitkey.account.AccountConfigService
import bitkey.auth.AccountAuthTokens
import bitkey.auth.AuthTokenScope
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId.AUTH_TOKENS_REFRESH_FOR_HW_POP_ERROR
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId.REFRESHING_AUTH_TOKENS_FOR_HW_POP
import build.wallet.auth.AuthTokensService
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.PublicKey
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.ktor.result.HttpError
import build.wallet.logging.logFailure
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachineImpl.State.AuthTokensRefreshError
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachineImpl.State.RefreshingAuthTokensState
import build.wallet.statemachine.core.*
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

interface RefreshAuthTokensUiStateMachine : StateMachine<RefreshAuthTokensProps, ScreenModel>

data class RefreshAuthTokensProps(
  val fullAccountId: FullAccountId,
  val appAuthKey: PublicKey<AppGlobalAuthKey>? = null,
  val onSuccess: (AccountAuthTokens) -> Unit,
  val onBack: () -> Unit,
  val onTokenRefresh: (() -> ScreenModel)? = null,
  val onTokenRefreshError: (
    (
      isConnectivityError: Boolean,
      onRetry: () -> Unit,
    ) -> ScreenModel
  )? = null,
  val screenPresentationStyle: ScreenPresentationStyle,
)

@BitkeyInject(ActivityScope::class)
class RefreshAuthTokensUiStateMachineImpl(
  private val authTokensService: AuthTokensService,
  private val accountConfigService: AccountConfigService,
) : RefreshAuthTokensUiStateMachine {
  @Composable
  override fun model(props: RefreshAuthTokensProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(RefreshingAuthTokensState) }
    return when (val state = uiState) {
      RefreshingAuthTokensState -> {
        LaunchedEffect("refresh-auth-tokens") {
          val f8eEnvironment = accountConfigService.activeOrDefaultConfig().value.f8eEnvironment
          authTokensService
            .refreshAccessTokenWithApp(f8eEnvironment, props.fullAccountId, AuthTokenScope.Global)
            .onSuccess { props.onSuccess(it) }
            .logFailure { "Unable to refresh auth token for proof of possession" }
            .onFailure { uiState = AuthTokensRefreshError(it) }
        }

        // Use the model provided by the props if present, otherwise use a loading screen model
        when (val onTokenRefresh = props.onTokenRefresh) {
          null ->
            LoadingBodyModel(
              title = "Authenticating with server...",
              id = REFRESHING_AUTH_TOKENS_FOR_HW_POP
            ).asScreen(props.screenPresentationStyle)

          else -> onTokenRefresh()
        }
      }

      is AuthTokensRefreshError -> {
        // Use the model provided by the props if present, otherwise use a default error screen model
        when (val onTokenRefreshError = props.onTokenRefreshError) {
          null ->
            ErrorFormBodyModel(
              title = "We were unable to continue authenticating you.",
              primaryButton =
                ButtonDataModel(
                  text = "Retry",
                  onClick = { uiState = RefreshingAuthTokensState }
                ),
              toolbar =
                ToolbarModel(
                  leadingAccessory =
                    IconAccessory.BackAccessory(
                      onClick = props.onBack
                    )
                ),
              onBack = props.onBack,
              eventTrackerScreenId = AUTH_TOKENS_REFRESH_FOR_HW_POP_ERROR
            ).asScreen(props.screenPresentationStyle)

          else ->
            onTokenRefreshError(state.error is HttpError.NetworkError) {
              uiState = RefreshingAuthTokensState
            }
        }
      }
    }
  }

  private sealed interface State {
    data object RefreshingAuthTokensState : State

    data class AuthTokensRefreshError(val error: Error) : State
  }
}
