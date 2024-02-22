package build.wallet.firmware

class TeltraMock : Teltra {
  override fun translateBitlogs(
    bitlogs: List<UByte>,
    identifiers: TelemetryIdentifiers,
  ): List<List<UByte>> {
    return emptyList()
  }
}
