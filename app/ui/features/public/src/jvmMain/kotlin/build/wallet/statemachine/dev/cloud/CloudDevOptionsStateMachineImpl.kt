package build.wallet.statemachine.dev.cloud

import androidx.compose.runtime.Composable
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.statemachine.core.BodyModel

@BitkeyInject(AppScope::class)
class CloudDevOptionsStateMachineImpl : CloudDevOptionsStateMachine {
  @Composable
  override fun model(props: CloudDevOptionsProps): BodyModel {
    TODO("Not yet implemented")
  }
}
