package build.wallet.nfc.haptics

import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NfcHapticsImpl(
  private val haptics: Haptics,
  private val nfcHapticsOnConnectedIsEnabledFeatureFlag: NfcHapticsOnConnectedIsEnabledFeatureFlag,
  private val appCoroutineScope: CoroutineScope,
) : NfcHaptics {
  private fun vibrate(effect: HapticsEffect) {
    appCoroutineScope.launch {
      haptics.vibrate(effect = effect)
    }
  }

  override fun vibrateConnection() {
    if (nfcHapticsOnConnectedIsEnabledFeatureFlag.flagValue().value.value) {
      vibrate(effect = HapticsEffect.DoubleClick)
    }
  }

  override fun vibrateSuccess() {
    vibrate(effect = HapticsEffect.DoubleClick)
  }

  override fun vibrateFailure() {
    vibrate(effect = HapticsEffect.DullOneShot)
  }
}
