package build.wallet.statemachine.platform.permissions

import androidx.compose.runtime.Composable
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Allows for requesting the system native notification permission
 */
interface NotificationPermissionRequester {
  /**
   * A composable function to be used within a StateMachine to request the notification permission
   *
   * @param onGranted - Called once the user grants the notification permission
   * @param onDeclined - Called once the user does any action other than granting permission
   * (ignore, decline, etc)
   */
  @OptIn(ExperimentalObjCRefinement::class)
  @Composable
  @HiddenFromObjC
  fun requestNotificationPermission(
    onGranted: () -> Unit,
    onDeclined: () -> Unit,
  )
}
