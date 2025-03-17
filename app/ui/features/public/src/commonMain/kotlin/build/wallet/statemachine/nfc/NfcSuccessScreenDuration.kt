package build.wallet.statemachine.nfc

import build.wallet.platform.device.DevicePlatform
import build.wallet.platform.device.DevicePlatform.Android
import build.wallet.platform.device.DevicePlatform.IOS
import build.wallet.platform.device.DevicePlatform.Jvm
import kotlin.time.Duration.Companion.seconds

/**
 * The delay with which to show the success message for NFC interactions.
 *
 * For iOS, this should roughly correspond with the OS-level delay of the popup window disappearing
 * since we do not have control of that window closing, so we want to wait to transition any of our
 * UI until it closes itself
 */
fun NfcSuccessScreenDuration(
  devicePlatform: DevicePlatform,
  isHardwareFake: Boolean,
) = when (devicePlatform) {
  // Don't use a delay if we're using fake hardware for iOS since this is just for the
  // OS-level bottom sheet that won't be shown with fake hardware
  IOS -> if (isHardwareFake) 0.seconds else 2.8.seconds
  Android -> 1.5.seconds
  Jvm -> 0.seconds
}
