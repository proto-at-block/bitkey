package build.wallet.nfc

import android.nfc.NfcAdapter
import build.wallet.platform.PlatformContext

/**
 * Android implementation of [NfcReaderCapability], uses Android SDK.
 */
actual class NfcReaderCapabilityImpl actual constructor(
  private val platformContext: PlatformContext,
) : NfcReaderCapability {
  actual override fun availability(isHardwareFake: Boolean): NfcAvailability {
    if (isHardwareFake) {
      return NfcAvailability.Available.Enabled
    }
    return when (val nfcAdapter = NfcAdapter.getDefaultAdapter(platformContext.appContext)) {
      null -> NfcAvailability.NotAvailable
      else ->
        when {
          nfcAdapter.isEnabled -> NfcAvailability.Available.Enabled
          else -> NfcAvailability.Available.Disabled
        }
    }
  }
}
