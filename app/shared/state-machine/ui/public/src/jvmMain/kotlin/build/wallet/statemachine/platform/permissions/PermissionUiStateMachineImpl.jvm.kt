package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.BodyModel

actual class PermissionUiStateMachineImpl : PermissionUiStateMachine {
  actual override val isImplemented: Boolean = false

  @Composable
  actual override fun model(props: PermissionUiProps): BodyModel {
    TODO("Not yet implemented")
  }
}
