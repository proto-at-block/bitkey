package build.wallet.nfc

fun interface NfcReaderCapabilityProvider {
  /**
   * Return either a real or mocked implementation of [NfcReaderCapability] based on [fakeHardware]
   * param.
   */
  fun get(isHardwareFake: Boolean): NfcReaderCapability
}
