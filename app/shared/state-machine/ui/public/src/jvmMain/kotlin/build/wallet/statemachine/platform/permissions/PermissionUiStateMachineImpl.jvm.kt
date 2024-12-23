package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel

@BitkeyInject(ActivityScope::class)
class PermissionUiStateMachineImpl : PermissionUiStateMachine {
  override val isImplemented: Boolean = false

  @Composable
  override fun model(props: PermissionUiProps): BodyModel {
    TODO("Not yet implemented")
  }
}
