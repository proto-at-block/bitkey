package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.ShowingSetupInstructionsUiState.AlertState
import kotlinx.coroutines.flow.Flow

interface NotificationPreferencesSetupPushItemModelProvider {
  /**
   * Allows platform-specific model for the push notifications line item shown in the
   * notification preferences setup screen.
   *
   * @param onShowAlert: Callback for the state machine to show an alert corresponding
   * to the given alert state for requesting push notifications
   */
  fun model(onShowAlert: (AlertState) -> Unit): Flow<NotificationPreferencesSetupFormItemModel>
}
