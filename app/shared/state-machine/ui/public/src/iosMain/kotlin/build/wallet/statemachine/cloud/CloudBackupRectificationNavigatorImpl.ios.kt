package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject

@BitkeyInject(ActivityScope::class)
class CloudBackupRectificationNavigatorImpl : CloudBackupRectificationNavigator {
  @Composable
  override fun navigate(
    data: Any,
    onReturn: () -> Unit,
  ) {
    error("This should only be implemented in Android")
  }
}
