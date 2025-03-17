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
      else -> NfcAvailability.Available.Disabled
    }
  }
}
