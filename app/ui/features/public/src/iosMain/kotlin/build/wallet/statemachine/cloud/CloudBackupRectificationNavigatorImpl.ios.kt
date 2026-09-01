package build.wallet.statemachine.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import build.wallet.di.ActivityScope
import build.wallet.di.BitkeyInject

/**
 * iOS rectification for cloud backup errors.
 *
 * Unlike Android, where a rectifiable error carries a `UserRecoverableAuthIOException` Intent that
 * launches an OS re-auth flow, CloudKit has no OS-level rectification screen. The rectification data
 * on iOS is only the underlying `NSError`, so there is nothing to navigate to — the correct recovery
 * is simply to retry the backup operation, which is what [onReturn] triggers.
 *
 * Previously this threw, which crashed the app when a user tapped "Try again" on a rectifiable
 * CloudKit error during onboarding (BKW-573).
 */
@BitkeyInject(ActivityScope::class)
class CloudBackupRectificationNavigatorImpl : CloudBackupRectificationNavigator {
  @Composable
  override fun navigate(
    data: Any,
    onReturn: () -> Unit,
  ) {
    val currentOnReturn by rememberUpdatedState(onReturn)
    LaunchedEffect("cloudkit-rectification-retry") {
      currentOnReturn()
    }
  }
}
