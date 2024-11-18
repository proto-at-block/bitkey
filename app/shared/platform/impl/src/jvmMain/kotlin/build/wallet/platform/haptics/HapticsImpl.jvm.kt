package build.wallet.platform.haptics

import build.wallet.platform.PlatformContext

actual class HapticsImpl actual constructor(
  platformContext: PlatformContext,
  hapticsPolicy: HapticsPolicy,
) : Haptics {
  actual override suspend fun vibrate(effect: HapticsEffect) {
    // noop
  }
}
