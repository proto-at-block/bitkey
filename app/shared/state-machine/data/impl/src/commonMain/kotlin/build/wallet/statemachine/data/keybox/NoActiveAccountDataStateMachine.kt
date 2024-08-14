package build.wallet.statemachine.data.keybox

import build.wallet.bitkey.account.Account
import build.wallet.debug.DebugOptions
import build.wallet.recovery.Recovery.StillRecovering
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.data.keybox.AccountData.NoActiveAccountData

/**
 * Manages state of the case when there is no active keybox present:
 * - there is an ongoing "Lost App" recovery
 * - onboarding of a new keybox is in progress
 */
interface NoActiveAccountDataStateMachine :
  StateMachine<NoActiveAccountDataProps, NoActiveAccountData>

data class NoActiveAccountDataProps(
  val debugOptions: DebugOptions,
  val existingRecovery: StillRecovering?,
  val onAccountCreated: (Account) -> Unit,
)
