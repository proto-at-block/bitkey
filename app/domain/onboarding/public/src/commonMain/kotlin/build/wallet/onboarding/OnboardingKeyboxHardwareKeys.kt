package build.wallet.onboarding

import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey

data class OnboardingKeyboxHardwareKeys(
  val hwAuthPublicKey: HwAuthPublicKey,
  val appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
)
