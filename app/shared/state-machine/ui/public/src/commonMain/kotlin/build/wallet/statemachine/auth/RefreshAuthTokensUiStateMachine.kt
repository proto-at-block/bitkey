package build.wallet.statemachine.auth

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId.AUTH_TOKENS_REFRESH_FOR_HW_POP_ERROR
import build.wallet.analytics.events.screen.id.AuthEventTrackerScreenId.REFRESHING_AUTH_TOKENS_FOR_HW_POP
import build.wallet.auth.AccountAuthTokens
import build.wallet.auth.AuthTokenScope
import build.wallet.auth.AuthTokensRepository
import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.ktor.result.HttpError
import build.wallet.logging.logFailure
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachineImpl.State.AuthTokensRefreshError
import build.wallet.statemachine.auth.RefreshAuthTokensUiStateMachineImpl.State.RefreshingAuthTokensState
import build.wallet.statemachine.core.ButtonDataModel
import build.wallet.statemachine.core.ErrorFormBodyModel
import build.wallet.statemachine.core.LoadingBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import build.wallet.ui.model.toolbar.ToolbarModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess

interface RefreshAuthTokensUiStateMachine : StateMachine<RefreshAuthTokensProps, ScreenModel>

data class RefreshAuthTokensProps(
  val fullAccountId: FullAccountId,
  val fullAccountConfig: FullAccountConfig,
  val appAuthKey: AppGlobalAuthPublicKey? = null,
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

class RefreshAuthTokensUiStateMachineImpl(
  private val authTokensRepository: AuthTokensRepository,
) : RefreshAuthTokensUiStateMachine {
  @Composable
  override fun model(props: RefreshAuthTokensProps): ScreenModel {
    var uiState: State by remember { mutableStateOf(RefreshingAuthTokensState) }
    return when (val state = uiState) {
      RefreshingAuthTokensState -> {
        LaunchedEffect("refresh-auth-tokens") {
          refreshAuthTokens(props)
            .onSuccess { props.onSuccess(it) }
            .logFailure { "Unable to refresh auth token for proof of possession" }
            .onFailure { uiState = AuthTokensRefreshError(it) }
        }

        // Use the model provided by the props if present, otherwise use a loading screen model
        when (val onTokenRefresh = props.onTokenRefresh) {
          null ->
            LoadingBodyModel(
              message = "Authenticating with server...",
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

  private suspend fun refreshAuthTokens(props: RefreshAuthTokensProps) =
    authTokensRepository.refreshAccessToken(
      f8eEnvironment = props.fullAccountConfig.f8eEnvironment,
      accountId = props.fullAccountId,
      scope = AuthTokenScope.Global
    )

  private sealed interface State {
    data object RefreshingAuthTokensState : State

    data class AuthTokensRefreshError(val error: Error) : State
  }
}
