package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable

actual class CloudBackupRectificationNavigatorImpl : CloudBackupRectificationNavigator {
  @Composable
  override fun navigate(
    data: Any,
    onReturn: () -> Unit,
  ) {
    error("This should only be implemented in Android")
  }
}
