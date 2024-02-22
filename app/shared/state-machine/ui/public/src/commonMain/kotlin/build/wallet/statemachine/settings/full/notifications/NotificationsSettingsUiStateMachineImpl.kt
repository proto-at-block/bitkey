package build.wallet.statemachine.settings.full.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import build.wallet.notifications.NotificationTouchpointType.Email
import build.wallet.notifications.NotificationTouchpointType.PhoneNumber
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle.Root
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationProps.EntryPoint.Settings
import build.wallet.statemachine.notifications.NotificationTouchpointInputAndVerificationUiStateMachine
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsUiStateMachineImpl.State.ShowingEmailInputAndVerificationUiState
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsUiStateMachineImpl.State.ShowingNotificationsSettingsUiState
import build.wallet.statemachine.settings.full.notifications.NotificationsSettingsUiStateMachineImpl.State.ShowingPhoneNumberInputAndVerificationUiState

class NotificationsSettingsUiStateMachineImpl(
  private val notificationTouchpointInputAndVerificationUiStateMachine:
    NotificationTouchpointInputAndVerificationUiStateMachine,
) : NotificationsSettingsUiStateMachine {
  @Composable
  override fun model(props: NotificationsSettingsProps): ScreenModel {
    var state: State by remember { mutableStateOf(ShowingNotificationsSettingsUiState) }

    return when (state) {
      is ShowingNotificationsSettingsUiState ->
        ScreenModel(
          body =
            NotificationsSettingsFormBodyModel(
              smsText = props.accountData.notificationTouchpointData.phoneNumber?.formattedDisplayValue,
              emailText = props.accountData.notificationTouchpointData.email?.value,
              onBack = props.onBack,
              onSmsClick = {
                state = ShowingPhoneNumberInputAndVerificationUiState
              },
              onEmailClick = {
                state = ShowingEmailInputAndVerificationUiState
              }
            ),
          presentationStyle = Root
        )

      is ShowingPhoneNumberInputAndVerificationUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              fullAccountId = props.accountData.account.accountId,
              keyboxConfig = props.accountData.account.keybox.config,
              touchpointType = PhoneNumber,
              entryPoint = Settings,
              onClose = {
                state = ShowingNotificationsSettingsUiState
              }
            )
        )
      }

      is ShowingEmailInputAndVerificationUiState -> {
        notificationTouchpointInputAndVerificationUiStateMachine.model(
          props =
            NotificationTouchpointInputAndVerificationProps(
              fullAccountId = props.accountData.account.accountId,
              keyboxConfig = props.accountData.account.keybox.config,
              touchpointType = Email,
              entryPoint = Settings,
              onClose = {
                state = ShowingNotificationsSettingsUiState
              }
            )
        )
      }
    }
  }

  private sealed class State {
    data object ShowingNotificationsSettingsUiState : State()

    data object ShowingPhoneNumberInputAndVerificationUiState : State()

    data object ShowingEmailInputAndVerificationUiState : State()
  }
}
