package build.wallet.statemachine.notifications

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.f8e.AccountId
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * Flow for gathering and verifying a notification touchpoint, either
 * - an sms (using [PhoneNumberInputStateMachine] for input),
 * - an email (using [EmailInputStateMachine] for input),
 * using [VerificationCodeInputStateMachine] for verification and [NotificationTouchpointF8eClient]
 * to interact with the server.
 *
 * Emits a [BodyModel] so that callers can determine presentation style of the entire flow.
 */
interface NotificationTouchpointInputAndVerificationUiStateMachine :
  StateMachine<NotificationTouchpointInputAndVerificationProps, ScreenModel>

data class NotificationTouchpointInputAndVerificationProps(
  val accountId: AccountId,
  val touchpointType: NotificationTouchpointType,
  val entryPoint: EntryPoint,
  val onClose: (() -> Unit)? = null,
  val onSuccess: () -> Unit,
  val onLearnMore: (() -> Unit)? = null,
) {
  sealed interface EntryPoint {
    /**
     * Settings entry point, where hardware authorization (W1 proof-of-possession or W3 action
     * proof) is needed for touchpoint activation.
     *
     * @property fullAccount The active full account for hardware authorization.
     */
    data class Settings(val fullAccount: FullAccount) : EntryPoint

    /**
     * Onboarding and recovery entry point.
     *
     * @property fullAccount The full account, if available. When provided and the account uses
     *   W3 hardware, hardware authorization (action proof) is required for touchpoint activation.
     *   When null or W1 hardware, activation proceeds without hardware verification.
     * @property onSkip Handler for when 'Skip' button is clicked.
     */
    data class OnboardingAndRecovery(
      val fullAccount: FullAccount? = null,
      val onSkip: (() -> Unit)? = null,
    ) : EntryPoint
  }
}
