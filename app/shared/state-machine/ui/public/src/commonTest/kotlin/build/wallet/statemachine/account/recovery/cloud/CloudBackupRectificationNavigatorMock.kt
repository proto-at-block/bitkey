package build.wallet.statemachine.account.recovery.cloud

import androidx.compose.runtime.Composable
import build.wallet.statemachine.cloud.CloudBackupRectificationNavigator

class CloudBackupRectificationNavigatorMock : CloudBackupRectificationNavigator {
  @Composable
  override fun navigate(
    data: Any,
    onReturn: () -> Unit,
  ) {
    onReturn()
  }
}
