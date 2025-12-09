package build.wallet.statemachine.biometric

import androidx.compose.runtime.*
import build.wallet.analytics.events.screen.EventTrackerScreenInfo
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.platform.biometrics.BiometricPrompter
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import build.wallet.ui.theme.Theme
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.time.Duration.Companion.seconds

@BitkeyInject(ActivityScope::class)
class BiometricPromptUiStateMachineImpl(
  private val appSessionManager: AppSessionManager,
  private val biometricPrompter: BiometricPrompter,
) : BiometricPromptUiStateMachine {
  @Composable
  override fun model(props: BiometricPromptProps): ScreenModel? {
    val appSessionState by remember {
      appSessionManager.appSessionState
    }.collectAsState()

    var uiState: UiState by remember(appSessionState) {
      if (props.shouldPromptForAuth) {
        mutableStateOf(UiState.Authenticating)
      } else {
        mutableStateOf(UiState.Authenticated)
      }
    }

    return when (uiState) {
      is UiState.Authenticating -> {
        if (appSessionState == AppSessionState.FOREGROUND) {
          AuthenticatingPromptEffect(
            biometricPrompter = biometricPrompter,
            onAuthenticated = { uiState = UiState.Authenticated },
            onAuthenticationFailed = { uiState = UiState.Locked }
          )
        }

        SplashBodyModel(
          bitkeyWordMarkAnimationDelay = 0.seconds,
          bitkeyWordMarkAnimationDuration = 0.seconds,
          eventTrackerScreenInfo = EventTrackerScreenInfo(
            eventTrackerScreenId = BiometricPromptScreenId.BIOMETRIC_PROMPT_SPLASH_SCREEN
          )
        ).asRootFullScreen(theme = Theme.DARK)
      }
      UiState.Authenticated -> null
      UiState.Locked -> SplashLockModel(
        unlockButtonModel = ButtonModel(
          text = "Unlock",
          treatment = ButtonModel.Treatment.Translucent,
          size = ButtonModel.Size.Footer,
          onClick = StandardClick {
            uiState = UiState.Authenticating
          }
        )
      ).asRootFullScreen(theme = Theme.DARK)
    }
  }
}

@Composable
private fun AuthenticatingPromptEffect(
  biometricPrompter: BiometricPrompter,
  onAuthenticated: () -> Unit,
  onAuthenticationFailed: () -> Unit,
) {
  LaunchedEffect("prompting-for-auth") {
    biometricPrompter.promptForAuth()
      .onSuccess { onAuthenticated() }
      .onFailure { onAuthenticationFailed() }
  }
}

private sealed interface UiState {
  /** The auth prompt is shown and going through the necessary steps to auth via biometrics */
  data object Authenticating : UiState

  /** Authentication has succeeded */
  data object Authenticated : UiState

  /** Authentication has failed and the lock ui is shown */
  data object Locked : UiState
}
