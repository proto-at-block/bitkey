package build.wallet.statemachine.data.recovery.conflict

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.f8e.F8eEnvironment
import build.wallet.statemachine.core.StateMachine

/**
 * Drives the logic for the screens shown when [Recovery] is in a state of [SomeoneElseIsRecovering].
 *
 * This state means that some other app / process (other than the current one) initiated a recovery,
 * so we want to show the current user an informative screen alerting them to that and asking them
 * to cancel the other recovery if they are the rightful owner.
 */
interface SomeoneElseIsRecoveringDataStateMachine :
  StateMachine<SomeoneElseIsRecoveringDataProps, SomeoneElseIsRecoveringData>

/**
 * @property onClose: Callback for the close button shown on the informative screen.
 */
data class SomeoneElseIsRecoveringDataProps(
  val cancelingRecoveryLostFactor: PhysicalFactor,
  val onClose: () -> Unit,
  val f8eEnvironment: F8eEnvironment,
  val fullAccountId: FullAccountId,
)
