package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable

expect class CloudBackupRectificationNavigatorImpl() : CloudBackupRectificationNavigator {
  @Composable
  override fun navigate(
    data: Any,
    onReturn: () -> Unit,
  )
}
