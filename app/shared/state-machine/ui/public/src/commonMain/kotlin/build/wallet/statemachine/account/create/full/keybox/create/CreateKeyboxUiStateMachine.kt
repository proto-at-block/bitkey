package build.wallet.statemachine.account.create.full.keybox.create

import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData

/**
 * A state machine for creating a brand new keybox.
 */
interface CreateKeyboxUiStateMachine : StateMachine<CreateKeyboxUiProps, ScreenModel>

/**
 * @property createKeyboxData - Data for managing the state of the keybox creation
 */
data class CreateKeyboxUiProps(
  val createKeyboxData: CreateFullAccountData.CreateKeyboxData,
  val isHardwareFake: Boolean,
)
