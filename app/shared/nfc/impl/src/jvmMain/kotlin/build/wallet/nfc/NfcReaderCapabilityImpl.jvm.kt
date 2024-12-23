package build.wallet.nfc

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.nfc.NfcAvailability.Available

/**
 * Android implementation of [NfcReaderCapability], uses Android SDK.
 */

@BitkeyInject(AppScope::class)
class NfcReaderCapabilityImpl : NfcReaderCapability {
  // Enabled for JVM tests
  override fun availability(isHardwareFake: Boolean): NfcAvailability = Available.Enabled
}
