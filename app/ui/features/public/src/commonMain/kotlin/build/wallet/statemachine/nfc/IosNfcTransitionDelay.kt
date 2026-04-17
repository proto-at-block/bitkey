package build.wallet.statemachine.nfc

import build.wallet.platform.device.DevicePlatform
import kotlinx.coroutines.delay

private const val IOS_NATIVE_NFC_MODAL_TRANSITION_DELAY_MILLIS = 400L

/**
 * Gives the in-app modal transition a brief head start before iOS presents the native NFC sheet.
 * Without this, CoreNFC can visually cover the bottom-slide animation immediately.
 */
internal suspend fun delayForIosNativeNfcTransition(
  designSystemV2Enabled: Boolean,
  devicePlatform: DevicePlatform,
) {
  if (designSystemV2Enabled && devicePlatform == DevicePlatform.IOS) {
    delay(IOS_NATIVE_NFC_MODAL_TRANSITION_DELAY_MILLIS)
  }
}
