package build.wallet.statemachine.platform.permissions

import build.wallet.platform.permissions.Permission
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.StateMachine

/**
 * Permission state machine that gates a [StateMachine] [model] behind the state of the system
 * permission
 */
interface PermissionUiStateMachine : StateMachine<PermissionUiProps, BodyModel> {
  /**
   * Whether or not this state machine is implemented on the given platform
   * If not (on iOS), ignore this state machine. The permissions logic is instead baked in to
   * the views that require it.
   */
  val isImplemented: Boolean
}

data class PermissionUiProps(
  val permission: Permission,
  val onExit: () -> Unit,
  val onGranted: () -> Unit,
)
