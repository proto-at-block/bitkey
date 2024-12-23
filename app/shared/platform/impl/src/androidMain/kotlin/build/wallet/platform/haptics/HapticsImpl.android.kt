package build.wallet.platform.haptics

import android.os.Vibrator
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class HapticsImpl(
  private val vibrator: Vibrator?,
  private val hapticsPolicy: HapticsPolicy,
) : Haptics {
  override suspend fun vibrate(effect: HapticsEffect) {
    if (hapticsPolicy.canVibrate()) {
      vibrator?.maybeVibrate(effect)
    }
  }
}
