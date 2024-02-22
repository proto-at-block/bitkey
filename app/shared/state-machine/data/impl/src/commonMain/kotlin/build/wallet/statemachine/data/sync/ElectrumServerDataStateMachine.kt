package build.wallet.statemachine.data.sync

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine to load Electrum information from F8e and supply host information for the BDK
 * wallet to connect to.
 */
interface ElectrumServerDataStateMachine : StateMachine<ElectrumServerDataProps, ElectrumServerData>

data class ElectrumServerDataProps(
  val f8eEnvironment: F8eEnvironment,
  val network: BitcoinNetworkType,
)
