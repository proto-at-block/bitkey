package build.wallet.nfc

import build.wallet.platform.PlatformContext

expect class NfcReaderCapabilityImpl(
  platformContext: PlatformContext,
) : NfcReaderCapability {
  override fun availability(isHardwareFake: Boolean): NfcAvailability
}
