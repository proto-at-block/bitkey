package build.wallet.statemachine.biometric

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the biometric prompt UI shown when the user is prompted to authenticate with biometrics.
 */
interface BiometricPromptUiStateMachine : StateMachine<Unit, ScreenModel?>
