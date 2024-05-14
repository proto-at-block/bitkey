package build.wallet.statemachine.home.lite

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.firmware.FirmwareData
import build.wallet.statemachine.data.keybox.AccountData

/**
 * State machine for managing the home (money home + settings) experiences for a "lite" customer.
 */
interface LiteHomeUiStateMachine : StateMachine<LiteHomeUiProps, ScreenModel>

data class LiteHomeUiProps(
  val accountData: AccountData.HasActiveLiteAccountData,
  val firmwareData: FirmwareData,
)
