package build.wallet.nfc

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import platform.CoreNFC.NFCReaderSession

/**
 * iOS implementation of [NfcReaderCapability], uses CoreNFC APIs.
 */

@BitkeyInject(AppScope::class)
class NfcReaderCapabilityImpl : NfcReaderCapability {
  override fun availability(isHardwareFake: Boolean): NfcAvailability {
    if (isHardwareFake) {
      return NfcAvailability.Available.Enabled
    }
    return when {
      NFCReaderSession.readingAvailable -> NfcAvailability.Available.Enabled
      // iOS devices either have NFC hardware or they don't - there's no way to
      // disable NFC like on Android. When readingAvailable is false, the device
      // doesn't support NFC (e.g., iPod Touch, iPads running the app in compatibility mode).
      else -> NfcAvailability.NotAvailable
    }
  }
}
