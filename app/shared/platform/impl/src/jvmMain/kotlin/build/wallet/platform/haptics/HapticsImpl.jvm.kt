package build.wallet.platform.haptics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

@BitkeyInject(AppScope::class)
class HapticsImpl : Haptics {
  override suspend fun vibrate(effect: HapticsEffect) {
    // noop
  }
}
