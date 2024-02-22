package build.wallet.platform.haptics

import build.wallet.platform.PlatformContext

actual class HapticsImpl actual constructor(
  platformContext: PlatformContext,
  @Suppress("unused")
  private val hapticsPolicy: HapticsPolicy,
) : Haptics {
  override suspend fun vibrate(effect: HapticsEffect) {
    // No-op, let the system handle vibrations for now since they
    // are used for NFC which is all handled by the OS on iOS.
  }
}
