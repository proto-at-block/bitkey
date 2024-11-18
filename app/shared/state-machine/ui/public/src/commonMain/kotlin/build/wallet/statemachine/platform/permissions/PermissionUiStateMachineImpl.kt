package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.BodyModel

expect class PermissionUiStateMachineImpl constructor() : PermissionUiStateMachine {
  override val isImplemented: Boolean

  @Composable
  override fun model(props: PermissionUiProps): BodyModel
}
