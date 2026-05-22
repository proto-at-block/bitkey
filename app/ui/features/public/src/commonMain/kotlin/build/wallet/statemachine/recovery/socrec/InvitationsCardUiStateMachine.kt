package build.wallet.statemachine.recovery.socrec

import build.wallet.LoadableValue
import build.wallet.bitkey.relationships.TrustedContact
import build.wallet.statemachine.core.StateMachine
import build.wallet.statemachine.moneyhome.card.CardModel
import kotlinx.collections.immutable.ImmutableList

/**
 * State machine for displaying a list of pending Recovery Contacts.
 */
interface RecoveryContactCardsUiStateMachine :
  StateMachine<RecoveryContactCardsUiProps, LoadableValue<ImmutableList<CardModel>>>

data class RecoveryContactCardsUiProps(
  val onClick: (TrustedContact) -> Unit,
)
