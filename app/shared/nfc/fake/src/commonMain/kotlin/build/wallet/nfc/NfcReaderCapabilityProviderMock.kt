package build.wallet.nfc

class NfcReaderCapabilityProviderMock : NfcReaderCapabilityProvider {
  override fun get(isHardwareFake: Boolean): NfcReaderCapability {
    return NfcReaderCapabilityMock()
  }
}
