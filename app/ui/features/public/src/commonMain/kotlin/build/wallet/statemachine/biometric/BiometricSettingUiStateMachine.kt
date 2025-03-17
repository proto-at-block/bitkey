package build.wallet.statemachine.biometric

import build.wallet.bitkey.keybox.Keybox
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine which manages the biometric preference and enabling it via OS calls
 */
interface BiometricSettingUiStateMachine : StateMachine<BiometricSettingUiProps, ScreenModel>

/**
 * The Props for launching [BiometricSettingUiStateMachine]
 *
 * @property onBack - Invoked on back of the UI
 */
data class BiometricSettingUiProps(
  val keybox: Keybox,
  val onBack: () -> Unit,
)
