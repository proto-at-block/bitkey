package build.wallet.statemachine.home.full

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.HasActiveFullAccountData.ActiveFullAccountLoadedData

/**
 * State machine for managing the home (money home + settings) experiences for a "full" customer.
 */
interface HomeUiStateMachine : StateMachine<HomeUiProps, ScreenModel>

data class HomeUiProps(
  val accountData: ActiveFullAccountLoadedData,
)
