package build.wallet.statemachine.settings.full.electrum

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine to present screens for setting up a custom Electrum server.
 *
 * Specifically, this state machine coordinates the decision around what screen should be presented
 * throughout the set up flow.
 */

interface CustomElectrumServerSettingUiStateMachine : StateMachine<CustomElectrumServerProps, ScreenModel>

/**
 * Props defining the user's current Electrum server
 */
data class CustomElectrumServerProps(
  val onBack: () -> Unit,
)
