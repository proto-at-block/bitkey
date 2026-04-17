package build.wallet.statemachine.recovery.conflict

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * UI state machine for handling recovery conflicts where another app/device is recovering.
 */
interface SomeoneElseIsRecoveringUiStateMachine :
  StateMachine<SomeoneElseIsRecoveringUiProps, ScreenModel>

data class SomeoneElseIsRecoveringUiProps(
  val cancelingRecoveryLostFactor: PhysicalFactor,
  val fullAccountId: FullAccountId,
  val onClose: () -> Unit = {},
)
