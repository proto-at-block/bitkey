package build.wallet.statemachine.notifications

import bitkey.notifications.NotificationChannel
import build.wallet.bitkey.f8e.AccountId
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

interface NotificationPreferencesUiStateMachine :
  StateMachine<NotificationPreferencesProps, ScreenModel>

data class NotificationPreferencesProps(
  val accountId: AccountId,
  // These are coming only from onboarding for initial setup
  val onboardingRecoveryChannelsEnabled: Set<NotificationChannel> = emptySet(),
  val source: Source,
  val onBack: () -> Unit,
  val onComplete: () -> Unit,
) {
  enum class Source {
    Onboarding,
    Settings,
  }
}
