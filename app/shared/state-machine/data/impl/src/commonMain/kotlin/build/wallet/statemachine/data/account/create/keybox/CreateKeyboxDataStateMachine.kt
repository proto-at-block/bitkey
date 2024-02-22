package build.wallet.statemachine.data.account.create.keybox

import build.wallet.bitkey.keybox.KeyboxConfig
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
 * @property templateKeyboxConfig [KeyboxConfig] to use for creating new Keybox.
 */
data class CreateKeyboxDataProps(
  val templateKeyboxConfig: KeyboxConfig,
  val context: CreateFullAccountContext,
  val rollback: () -> Unit,
)
