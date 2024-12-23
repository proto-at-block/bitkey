package build.wallet.nfc.haptics

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.feature.flags.NfcHapticsOnConnectedIsEnabledFeatureFlag
import build.wallet.platform.haptics.Haptics
import build.wallet.platform.haptics.HapticsEffect
import io.ktor.util.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@BitkeyInject(AppScope::class)
class NfcHapticsImpl(
  private val haptics: Haptics,
  private val nfcHapticsOnConnectedIsEnabledFeatureFlag: NfcHapticsOnConnectedIsEnabledFeatureFlag,
  private val appCoroutineScope: CoroutineScope,
) : NfcHaptics {
  private fun vibrate(effect: HapticsEffect) {
    if (PlatformUtils.IS_NATIVE) {
      // No-op, let the system handle vibrations for now since they
      // are used for NFC which is all handled by the OS on iOS.
      return
    }
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
