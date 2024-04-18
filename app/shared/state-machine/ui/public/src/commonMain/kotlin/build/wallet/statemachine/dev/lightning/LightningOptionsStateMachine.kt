package build.wallet.statemachine.dev.lightning

import build.wallet.statemachine.core.StateMachine
import build.wallet.ui.model.list.ListGroupModel

/**
 * State machine for showing debug options to enable/disable Lightning.
 */
interface LightningOptionsUiStateMachine : StateMachine<LightningOptionsUiProps, ListGroupModel>

/**
 * @property [onLightningOptionsClick] called when "Lightning Options" is pressed.
 */
data class LightningOptionsUiProps(
  val onLightningOptionsClick: () -> Unit,
)
