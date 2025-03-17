package build.wallet.statemachine.account.create.full

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.CloudBackupV2
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine for onboarding a brand full account.
 *
 * Responsible for backing up account to cloud storage and setting
 * up notification touchpoints (i.e. push, sms, and email).
 */
interface OnboardFullAccountUiStateMachine : StateMachine<OnboardFullAccountUiProps, ScreenModel>

data class OnboardFullAccountUiProps(
  val fullAccount: FullAccount,
  val isSkipCloudBackupInstructions: Boolean,
  val onFoundLiteAccountWithDifferentId: (cloudBackup: CloudBackupV2) -> Unit,
  val onOverwriteFullAccountCloudBackupWarning: () -> Unit,
  val onOnboardingComplete: () -> Unit,
)
