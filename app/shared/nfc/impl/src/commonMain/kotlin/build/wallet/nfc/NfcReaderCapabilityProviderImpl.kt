package build.wallet.nfc

import build.wallet.platform.PlatformContext

class NfcReaderCapabilityProviderImpl(
  private val platformContext: PlatformContext,
) : NfcReaderCapabilityProvider {
  override fun get(isHardwareFake: Boolean): NfcReaderCapability {
    return NfcReaderCapabilityImpl(platformContext, isHardwareFake)
  }
}
