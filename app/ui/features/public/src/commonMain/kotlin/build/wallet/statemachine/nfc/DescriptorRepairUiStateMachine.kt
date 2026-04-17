package build.wallet.statemachine.nfc

import build.wallet.bitkey.account.FullAccount
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.ScreenPresentationStyle
import build.wallet.statemachine.core.StateMachine

/**
 * Repairs a W3 hardware device that is missing its wallet descriptor.
 *
 * This is triggered when an NFC operation fails with `DescriptorNotLoaded`, meaning
 * the device never received its descriptor — typically because the user restored from
 * a cloud backup after uninstalling before the `BuildHardwareDescriptor` onboarding
 * step completed.
 *
 * The flow:
 * 1. Call the server (`completeOnboardingV2`) to get the WSM signature
 * 2. Prompt the user to tap their Bitkey device
 * 3. Deliver the descriptor via NFC
 * 4. Call [onRepairComplete] so the caller can retry the original operation
 */
interface DescriptorRepairUiStateMachine :
  StateMachine<DescriptorRepairUiProps, ScreenModel>

data class DescriptorRepairUiProps(
  val fullAccount: FullAccount,
  val presentationStyle: ScreenPresentationStyle,
  val onRepairComplete: () -> Unit,
  val onBack: () -> Unit,
)
