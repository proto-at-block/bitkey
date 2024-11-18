package build.wallet.nfc

import build.wallet.nfc.NfcAvailability.Available

class NfcReaderCapabilityMock(
  var availability: NfcAvailability = Available.Enabled,
) : NfcReaderCapability {
  override fun availability(isHardwareFake: Boolean): NfcAvailability = availability

  fun reset() {
    availability = Available.Enabled
  }
}
