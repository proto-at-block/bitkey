package build.wallet.statemachine.account.create.full.onboard.notifications

import build.wallet.statemachine.account.create.full.onboard.notifications.NotificationPreferencesSetupUiStateMachineImpl.State.ShowingSetupInstructionsUiState.AlertState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map

class NotificationPreferencesSetupPushItemModelProviderMock :
  NotificationPreferencesSetupPushItemModelProvider {
  var stateFlow = MutableSharedFlow<NotificationPreferencesSetupFormItemModel.State>()

  override fun model(onShowAlert: (AlertState) -> Unit) =
    stateFlow.map {
      NotificationPreferencesSetupFormItemModel(
        state = it,
        onClick = { onShowAlert(AlertState.SystemPromptRequestingPush) }
      )
    }
}
