package build.wallet.statemachine.data.recovery.sweep

import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.Keybox
import build.wallet.statemachine.core.StateMachine

/**
 * Data state machine for managing sweep, if any.
 */
interface SweepDataStateMachine : StateMachine<SweepDataProps, SweepData>

/**
 * @property keybox a keybox to perform sweep for.
 * @property onSuccess callback called by this state machine when sweep is
 * successfully, signed and broadcasted.
 */
data class SweepDataProps(
  val recoveredFactor: PhysicalFactor,
  val keybox: Keybox,
  val onSuccess: () -> Unit,
)
