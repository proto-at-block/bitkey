package build.wallet.statemachine.data.account.create.keybox

import build.wallet.bitkey.account.FullAccountConfig
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.account.CreateFullAccountData
import build.wallet.statemachine.data.account.create.CreateFullAccountContext

/**
 * Data state machine for managing creation of a Keybox, but not activation!
 *
 * Responsible for generating app keys, adding hardware keys (for now relying on parent to
 * pass hardware keys through data), pairing with server and creating new (but not active!) keybox.
 */
interface CreateKeyboxDataStateMachine :
  StateMachine<CreateKeyboxDataProps, CreateFullAccountData.CreateKeyboxData>

/**
 * @property templateFullAccountConfig [FullAccountConfig] to use for creating new Keybox.
 */
data class CreateKeyboxDataProps(
  val templateFullAccountConfig: FullAccountConfig,
  val context: CreateFullAccountContext,
  val rollback: () -> Unit,
)
