package build.wallet.statemachine.biometric

import androidx.compose.runtime.*
import build.wallet.inappsecurity.BiometricPreference
import build.wallet.platform.app.AppSessionManager
import build.wallet.platform.app.AppSessionState
import build.wallet.platform.biometrics.BiometricPrompter
import build.wallet.statemachine.core.ScreenColorMode
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SplashBodyModel
import build.wallet.statemachine.core.SplashLockModel
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.onSuccess
import kotlin.time.Duration.Companion.seconds

class BiometricPromptUiStateMachineImpl(
  private val appSessionManager: AppSessionManager,
  private val biometricPrompter: BiometricPrompter,
  private val biometricPreference: BiometricPreference,
) : BiometricPromptUiStateMachine {
  @Composable
  override fun model(props: Unit): ScreenModel? {
    val appSessionState by remember {
      appSessionManager.appSessionState
    }.collectAsState()

    var uiState: UiState by remember(appSessionState) {
      mutableStateOf(UiState.Authenticating)
    }

    return when (uiState) {
      is UiState.Authenticating -> {
        if (appSessionState == AppSessionState.FOREGROUND) {
          AuthenticatingPromptEffect(
            biometricPrompter = biometricPrompter,
            biometricPreference = biometricPreference,
            onAuthenticated = { uiState = UiState.Authenticated },
            onAuthenticationFailed = { uiState = UiState.Locked }
          )
        }

        SplashBodyModel(
          bitkeyWordMarkAnimationDelay = 0.seconds,
          bitkeyWordMarkAnimationDuration = 0.seconds
        ).asRootFullScreen(colorMode = ScreenColorMode.Dark)
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
      ).asRootFullScreen(colorMode = ScreenColorMode.Dark)
    }
  }
}

@Composable
private fun AuthenticatingPromptEffect(
  biometricPrompter: BiometricPrompter,
  biometricPreference: BiometricPreference,
  onAuthenticated: () -> Unit,
  onAuthenticationFailed: () -> Unit,
) {
  LaunchedEffect("prompting-for-auth") {
    biometricPreference.get()
      .onSuccess { enabled ->
        if (enabled) {
          biometricPrompter.promptForAuth()
            .result
            .onSuccess { onAuthenticated() }
            .onFailure { onAuthenticationFailed() }
        } else {
          onAuthenticated()
        }
      }
      .onFailure {
        onAuthenticated()
      }
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
