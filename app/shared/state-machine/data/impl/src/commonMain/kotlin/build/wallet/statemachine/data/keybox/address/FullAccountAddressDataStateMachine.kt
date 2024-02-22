package build.wallet.statemachine.data.keybox.address

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine for managing Full Account's receiving address.
 */
interface FullAccountAddressDataStateMachine : StateMachine<FullAccountAddressDataProps, KeyboxAddressData>

data class FullAccountAddressDataProps(
  val account: FullAccount,
)
