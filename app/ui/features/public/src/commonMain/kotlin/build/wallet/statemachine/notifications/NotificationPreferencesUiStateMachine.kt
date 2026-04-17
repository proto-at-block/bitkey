package build.wallet.statemachine.notifications

import bitkey.notifications.NotificationChannel
import build.wallet.bitkey.account.FullAccount
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
  /**
   * Full account, required for Settings entry point where hardware authorization
   * (action proof signing) is needed for preference updates.
   * Null for Onboarding where no action proof is required.
   */
  val fullAccount: FullAccount? = null,
) {
  enum class Source {
    Onboarding,
    Settings,
  }
}
