package build.wallet.statemachine.account.create.full.onboard

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachine

/**
 * A state machine for building hardware descriptor during full account onboarding.
 *
 * This step is only shown for W3 hardware devices and is responsible for
 * constructing the hardware descriptor needed for the account.
 */
interface BuildHardwareDescriptorUiStateMachine :
  StateMachine<BuildHardwareDescriptorUiProps, ScreenModel>

data class BuildHardwareDescriptorUiProps(
  val fullAccount: FullAccount,
  val onComplete: () -> Unit,
  val onBackupFailed: (Throwable) -> Unit,
)
