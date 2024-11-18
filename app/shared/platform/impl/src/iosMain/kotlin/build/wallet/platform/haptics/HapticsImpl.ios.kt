package build.wallet.platform.haptics

import build.wallet.platform.PlatformContext
import kotlinx.coroutines.delay
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle.*
import platform.UIKit.UISelectionFeedbackGenerator

actual class HapticsImpl actual constructor(
  platformContext: PlatformContext,
  @Suppress("unused")
  private val hapticsPolicy: HapticsPolicy,
) : Haptics {
  private val selectionGenerator = UISelectionFeedbackGenerator()

  actual override suspend fun vibrate(effect: HapticsEffect) {
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
      HapticsEffect.Selection -> {
        selectionGenerator.selectionChanged()
        return
      }
      HapticsEffect.LightClick -> UIImpactFeedbackStyleLight
      HapticsEffect.MediumClick -> UIImpactFeedbackStyleMedium
      HapticsEffect.HeavyClick -> UIImpactFeedbackStyleHeavy
    }
    UIImpactFeedbackGenerator(style).impactOccurred()
  }
}
