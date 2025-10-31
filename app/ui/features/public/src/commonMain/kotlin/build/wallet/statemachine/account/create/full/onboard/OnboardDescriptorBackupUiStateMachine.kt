package build.wallet.statemachine.account.create.full.onboard

import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine for onboarding descriptor backup during full account creation.
 *
 * Responsible for generating SSEK (if needed), sealing it with hardware, and
 * uploading the descriptor backup to the server.
 */
interface OnboardDescriptorBackupUiStateMachine :
  StateMachine<OnboardDescriptorBackupUiProps, ScreenModel>

data class OnboardDescriptorBackupUiProps(
  val fullAccount: FullAccount,
  val sealedSsek: SealedSsek?,
  val onBackupComplete: () -> Unit,
  val onBackupFailed: (Throwable) -> Unit,
)
