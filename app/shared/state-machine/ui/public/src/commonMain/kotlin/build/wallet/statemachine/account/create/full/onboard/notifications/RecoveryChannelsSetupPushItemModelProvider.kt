package build.wallet.statemachine.account.create.full.onboard.notifications

import kotlinx.coroutines.flow.Flow

interface RecoveryChannelsSetupPushItemModelProvider {
  /**
   * Allows platform-specific model for the push notifications line item shown in the
   * notification preferences setup screen.
   *
   * @param onShowAlert: Callback for the state machine to show an alert corresponding
   * to the given alert state for requesting push notifications
   */
  fun model(
    onShowAlert: (RecoveryChannelsSetupPushActionState) -> Unit,
  ): Flow<RecoveryChannelsSetupFormItemModel>
}

sealed interface RecoveryChannelsSetupPushActionState {
  /** The app prompt to request push notifications */
  data object AppInfoPromptRequestingPush : RecoveryChannelsSetupPushActionState

  /**
   * The iOS-specific alert to enable push notification permissions via opening settings
   * after initially denying them.
   * @param openAction: The action to open the app-specific settings on iOS
   */
  data class OpenSettings(val openAction: () -> Unit) : RecoveryChannelsSetupPushActionState
}
