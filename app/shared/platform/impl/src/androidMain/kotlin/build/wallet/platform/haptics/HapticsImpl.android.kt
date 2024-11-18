package build.wallet.platform.haptics

import android.os.Vibrator
import build.wallet.platform.PlatformContext

actual class HapticsImpl actual constructor(
  platformContext: PlatformContext,
  private val hapticsPolicy: HapticsPolicy,
) : Haptics {
  private val vibrator: Vibrator? by lazy { platformContext.appContext.vibrator() }

  actual override suspend fun vibrate(effect: HapticsEffect) {
    if (hapticsPolicy.canVibrate()) {
      vibrator?.maybeVibrate(effect)
    }
  }
}
