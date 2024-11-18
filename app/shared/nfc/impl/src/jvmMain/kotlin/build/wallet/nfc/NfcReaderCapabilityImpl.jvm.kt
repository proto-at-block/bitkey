package build.wallet.nfc

import build.wallet.nfc.NfcAvailability.Available
import build.wallet.platform.PlatformContext

/**
 * Android implementation of [NfcReaderCapability], uses Android SDK.
 */
actual class NfcReaderCapabilityImpl actual constructor(
  platformContext: PlatformContext,
) : NfcReaderCapability {
  // Enabled for JVM tests
  actual override fun availability(isHardwareFake: Boolean): NfcAvailability = Available.Enabled
}
