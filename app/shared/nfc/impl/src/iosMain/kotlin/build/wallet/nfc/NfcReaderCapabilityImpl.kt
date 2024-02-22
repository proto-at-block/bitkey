package build.wallet.nfc

import build.wallet.platform.PlatformContext
import platform.CoreNFC.NFCReaderSession

/**
 * iOS implementation of [NfcReaderCapability], uses CoreNFC APIs.
 */
actual class NfcReaderCapabilityImpl actual constructor(
  platformContext: PlatformContext,
  private var isHardwareFake: Boolean,
) : NfcReaderCapability {
  override fun availability(): NfcAvailability {
    if (isHardwareFake) {
      return NfcAvailability.Available.Enabled
    }
    return when {
      NFCReaderSession.readingAvailable -> NfcAvailability.Available.Enabled
      else -> NfcAvailability.Available.Disabled
    }
  }
}
