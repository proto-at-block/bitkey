package build.wallet.onboarding

import build.wallet.bitkey.account.FullAccount
import build.wallet.nfc.NfcSession
import build.wallet.nfc.platform.NfcCommands
import com.github.michaelbull.result.Result

/**
 * Service that delivers the hardware wallet descriptor to the device when it's missing.
 *
 * This handles the case where a W3 user creates an account and cloud backup but uninstalls
 * before completing the `BuildHardwareDescriptor` onboarding step. On cloud restoration,
 * the device lacks the wallet descriptor, causing `DescriptorNotLoaded` errors on any
 * wallet operation.
 *
 * The delivery flow:
 * 1. Call `completeOnboardingV2` to get the WSM signature (requires network)
 * 2. Extract keys from the keybox (local)
 * 3. Call `verifyKeysAndBuildDescriptor` on the hardware via NFC
 */
interface HardwareDescriptorDeliveryService {
  /**
   * Calls the server (`completeOnboardingV2`) to obtain the WSM signature, then
   * extracts keys from the account's active spending keyset and returns an NFC
   * session lambda that will call `verifyKeysAndBuildDescriptor` on the hardware.
   *
   * Requires network access for the server call.
   *
   * @param account The full account whose active keyset should be delivered
   * @return A suspend function that performs the NFC descriptor delivery when given a session and commands
   */
  suspend fun fetchSignatureAndPrepareNfcSession(
    account: FullAccount,
  ): Result<suspend (NfcSession, NfcCommands) -> String, Error>
}
