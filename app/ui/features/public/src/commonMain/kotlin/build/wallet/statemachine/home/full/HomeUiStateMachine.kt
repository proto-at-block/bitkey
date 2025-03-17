package build.wallet.statemachine.home.full

import build.wallet.bitkey.account.Account
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.recovery.losthardware.LostHardwareRecoveryData

/**
 * State machine for managing the home (money home + settings) experiences for a "full" customer.
 */
interface HomeUiStateMachine : StateMachine<HomeUiProps, ScreenModel>

data class HomeUiProps(
  val account: Account,
  val lostHardwareRecoveryData: LostHardwareRecoveryData,
)
