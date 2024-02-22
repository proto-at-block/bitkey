package build.wallet.statemachine.notifications

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.keybox.KeyboxConfig
import build.wallet.notifications.NotificationTouchpointType
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.SheetModel
import build.wallet.statemachine.core.StateMachine

/**
 * Flow for gathering and verifying a notification touchpoint, either
 * - an sms (using [PhoneNumberInputStateMachine] for input),
 * - an email (using [EmailInputStateMachine] for input),
 * using [VerificationCodeInputStateMachine] for verification and [NotificationTouchpointService]
 * to interact with the server.
 *
 * Emits a [BodyModel] so that callers can determine presentation style of the entire flow.
 */
interface NotificationTouchpointInputAndVerificationUiStateMachine :
  StateMachine<NotificationTouchpointInputAndVerificationProps, ScreenModel>

data class NotificationTouchpointInputAndVerificationProps(
  val fullAccountId: FullAccountId,
  val keyboxConfig: KeyboxConfig,
  val touchpointType: NotificationTouchpointType,
  val entryPoint: EntryPoint,
  val onClose: () -> Unit,
) {
  sealed interface EntryPoint {
    /**
     * @property onSkip: Handler for when 'Skip' button is clicked and we don't want to first show
     * a bottom sheet asking for confirmation, if allowed. If not allowed this will be null.
     * @property skipBottomSheetProvider: Bottom sheet to show asking for confirmation or informing
     * the user it is not allowed when a 'Skip' button is clicked.
     */
    data class Onboarding(
      val onSkip: (() -> Unit)?,
      val skipBottomSheetProvider: (onBack: () -> Unit) -> SheetModel,
    ) : EntryPoint

    data object Settings : EntryPoint
  }
}
