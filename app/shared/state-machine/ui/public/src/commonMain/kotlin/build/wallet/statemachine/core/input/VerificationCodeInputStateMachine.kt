package build.wallet.statemachine.core.input

import build.wallet.analytics.events.screen.id.EventTrackerScreenId
import build.wallet.notifications.NotificationTouchpoint
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * State machine for entering a verification code.
 */
interface VerificationCodeInputStateMachine : StateMachine<VerificationCodeInputProps, ScreenModel>

/**
 * @property title - the title to show at the top of the screen input
 * @property subtitle - the subtitle to show below the title
 * @property expectedCodeLength - the expected length of the code, used to determine when to call
 * the [onCodeEntered] callback
 * @property onBack - handler for back navigation.
 * @property onCodeEntered - handler for when a verification code is entered of the expected length.
 * @property onResendCode - handler for when the customer requests the code to be resent.
 * @property skipBottomSheetProvider - bottom sheet to show  when the customer requests to skip the
 * current input being verified. If null, no option to skip will be shown.
 */
data class VerificationCodeInputProps(
  val title: String,
  val subtitle: String,
  val expectedCodeLength: Int,
  val notificationTouchpoint: NotificationTouchpoint,
  val onBack: () -> Unit,
  val onCodeEntered: (String) -> Unit,
  val onResendCode: (callbacks: ResendCodeCallbacks) -> Unit,
  val skipBottomSheetProvider: ((onBack: () -> Unit) -> SheetModel)?,
  val screenId: EventTrackerScreenId?,
) {
  data class ResendCodeCallbacks(
    val onSuccess: () -> Unit,
    val onError: (isConnectivityError: Boolean) -> Unit,
  )
}
