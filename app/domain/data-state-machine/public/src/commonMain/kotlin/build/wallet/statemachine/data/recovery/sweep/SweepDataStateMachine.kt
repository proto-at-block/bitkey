package build.wallet.statemachine.data.recovery.sweep

import build.wallet.bitkey.keybox.Keybox
import build.wallet.recovery.sweep.SweepContext
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine for managing sweep, if any.
 */
interface SweepDataStateMachine : StateMachine<SweepDataProps, SweepData>

/**
 * @property hasAttemptedSweep whether the user has attempted to conduct a sweep during this
 * recovery attempt.
 * @property keybox a keybox to perform sweep for.
 * @property sweepContext the context in which the sweep is being performed.
 * @property onSuccess callback called by this state machine when sweep is
 * successfully, signed and broadcasted.
 */
data class SweepDataProps(
  val hasAttemptedSweep: Boolean,
  val onAttemptSweep: () -> Unit,
  val keybox: Keybox,
  val sweepContext: SweepContext = SweepContext.InactiveWallet,
  val onSuccess: () -> Unit,
)
