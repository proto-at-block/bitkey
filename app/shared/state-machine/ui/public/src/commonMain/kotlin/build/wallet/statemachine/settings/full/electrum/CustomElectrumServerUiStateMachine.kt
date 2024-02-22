package build.wallet.statemachine.settings.full.electrum

import build.wallet.bitcoin.sync.ElectrumServerPreferenceValue
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for the UI for setting a custom Electrum server. If turned on, it should also show
 * the Electrum server the user is connected to.
 */
interface CustomElectrumServerUiStateMachine : StateMachine<CustomElectrumServerUiProps, BodyModel>

/**
 * UI Props for the custom Electrum server setting screen.
 *
 * @property electrumServerPreferenceValue user's Electrum server preference setting.
 * @property onAdjustElectrumServerClick called when a user taps the action row to adjust their Electrum setting.
 * @property disableCustomElectrumServer turns off the custom Electrum server feature.
 */
data class CustomElectrumServerUiProps(
  val onBack: () -> Unit,
  val electrumServerPreferenceValue: ElectrumServerPreferenceValue,
  val onAdjustElectrumServerClick: () -> Unit,
  val disableCustomElectrumServer: () -> Unit,
)
