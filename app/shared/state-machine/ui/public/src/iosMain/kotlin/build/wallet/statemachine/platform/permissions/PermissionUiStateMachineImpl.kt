package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.statemachine.core.BodyModel

actual class PermissionUiStateMachineImpl :
  PermissionUiStateMachine {
  override val isImplemented = false

  @Composable
  override fun model(props: PermissionUiProps): BodyModel {
    TODO("Not yet implemented")
  }
}
