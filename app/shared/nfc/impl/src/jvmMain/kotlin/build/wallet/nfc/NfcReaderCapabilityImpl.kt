package build.wallet.nfc

import build.wallet.nfc.NfcAvailability.Available
import build.wallet.platform.PlatformContext

/**
 * Android implementation of [NfcReaderCapability], uses Android SDK.
 */
actual class NfcReaderCapabilityImpl actual constructor(
  platformContext: PlatformContext,
  isHardwareFake: Boolean,
) : NfcReaderCapability {
  // Enabled for JVM tests
  override fun availability(): NfcAvailability = Available.Enabled
}
