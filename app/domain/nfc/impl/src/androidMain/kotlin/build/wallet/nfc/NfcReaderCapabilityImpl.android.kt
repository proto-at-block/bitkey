package build.wallet.nfc

import android.app.Application
import android.nfc.NfcAdapter
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject

/**
 * Android implementation of [NfcReaderCapability], uses Android SDK.
 */

@BitkeyInject(AppScope::class)
class NfcReaderCapabilityImpl(
  private val application: Application,
) : NfcReaderCapability {
  override fun availability(isHardwareFake: Boolean): NfcAvailability {
    if (isHardwareFake) {
      return NfcAvailability.Available.Enabled
    }
    return when (val nfcAdapter = NfcAdapter.getDefaultAdapter(application)) {
      null -> NfcAvailability.NotAvailable
      else ->
        when {
          nfcAdapter.isEnabled -> NfcAvailability.Available.Enabled
          else -> NfcAvailability.Available.Disabled
        }
    }
  }
}
