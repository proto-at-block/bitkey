package build.wallet.platform.haptics

import build.wallet.platform.PlatformContext

actual class HapticsImpl actual constructor(
  platformContext: PlatformContext,
  hapticsPolicy: HapticsPolicy,
) : Haptics {
  override suspend fun vibrate(effect: HapticsEffect) {
    // noop
  }
}
