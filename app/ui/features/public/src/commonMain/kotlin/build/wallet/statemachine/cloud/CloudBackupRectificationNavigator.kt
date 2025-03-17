package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable

interface CloudBackupRectificationNavigator {
  @Composable
  fun navigate(
    data: Any,
    onReturn: () -> Unit,
  )
}
