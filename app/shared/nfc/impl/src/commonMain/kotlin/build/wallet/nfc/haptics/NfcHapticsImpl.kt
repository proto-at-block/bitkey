package build.wallet.nfc.haptics

import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class NfcHapticsImpl(
  private val haptics: Haptics,
  private val nfcHapticsIsEnabledFeatureFlag: NfcHapticsIsEnabledFeatureFlag,
  private val nfcHapticsOnConnectedIsEnabledFeatureFlag: NfcHapticsOnConnectedIsEnabledFeatureFlag,
  private val nfcHapticsOnFailureIsEnabledFeatureFlag: NfcHapticsOnFailureIsEnabledFeatureFlag,
  private val nfcHapticsOnSuccessIsEnabledFeatureFlag: NfcHapticsOnSuccessIsEnabledFeatureFlag,
  private val appCoroutineScope: CoroutineScope,
) : NfcHaptics {
  private fun vibrate(effect: HapticsEffect) {
    appCoroutineScope.launch {
      if (nfcHapticsIsEnabledFeatureFlag.flagValue().value.value) {
        haptics.vibrate(effect = effect)
      }
    }
  }

  override fun vibrateConnection() {
    if (nfcHapticsOnConnectedIsEnabledFeatureFlag.flagValue().value.value) {
      vibrate(effect = HapticsEffect.DoubleClick)
    }
  }

  override fun vibrateSuccess() {
    if (nfcHapticsOnSuccessIsEnabledFeatureFlag.flagValue().value.value) {
      vibrate(effect = HapticsEffect.DoubleClick)
    }
  }

  override fun vibrateFailure() {
    if (nfcHapticsOnFailureIsEnabledFeatureFlag.flagValue().value.value) {
      vibrate(effect = HapticsEffect.DullOneShot)
    }
  }
}
