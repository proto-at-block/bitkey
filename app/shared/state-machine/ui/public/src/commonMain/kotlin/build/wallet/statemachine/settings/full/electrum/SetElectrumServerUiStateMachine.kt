package build.wallet.statemachine.settings.full.electrum

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.sync.ElectrumServerDetails
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for entering a desired Electrum node endpoint to connect to.
 */
interface SetElectrumServerUiStateMachine : StateMachine<SetElectrumServerProps, ScreenModel>

data class SetElectrumServerProps(
  val onClose: () -> Unit,
  val currentElectrumServerDetails: ElectrumServerDetails?,
  val onSetServer: () -> Unit,
  val activeNetwork: BitcoinNetworkType,
)
