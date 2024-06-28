package build.wallet.platform.haptics

import build.wallet.platform.PlatformContext
import kotlinx.coroutines.delay
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle.*

actual class HapticsImpl actual constructor(
  platformContext: PlatformContext,
  @Suppress("unused")
  private val hapticsPolicy: HapticsPolicy,
) : Haptics {
  override suspend fun vibrate(effect: HapticsEffect) {
    val style = when (effect) {
      HapticsEffect.DoubleClick -> {
        with(UIImpactFeedbackGenerator(UIImpactFeedbackStyleSoft)) {
          impactOccurred()
          delay(100)
          impactOccurred()
        }
        return
      }
      HapticsEffect.DullOneShot -> UIImpactFeedbackStyleRigid
      HapticsEffect.MediumClick -> UIImpactFeedbackStyleMedium
    }
    UIImpactFeedbackGenerator(style).impactOccurred()
  }
}
