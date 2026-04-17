package build.wallet.platform.haptics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import kotlinx.coroutines.delay
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle.*
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType
import platform.UIKit.UISelectionFeedbackGenerator

@BitkeyInject(AppScope::class)
class HapticsImpl : Haptics {
  private val selectionGenerator = UISelectionFeedbackGenerator()
  private val notificationGenerator = UINotificationFeedbackGenerator()

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
      HapticsEffect.Selection -> {
        selectionGenerator.selectionChanged()
        return
      }
      HapticsEffect.Reject -> {
        notificationGenerator.notificationOccurred(UINotificationFeedbackType.UINotificationFeedbackTypeError)
        return
      }
      HapticsEffect.LightClick -> UIImpactFeedbackStyleLight
      HapticsEffect.MediumClick -> UIImpactFeedbackStyleMedium
      HapticsEffect.HeavyClick -> UIImpactFeedbackStyleHeavy
    }
    UIImpactFeedbackGenerator(style).impactOccurred()
  }
}
