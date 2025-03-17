package build.wallet.firmware

interface Teltra {
  /** Returns Memfault chunks translated from bitlogs, or an error. */
  fun translateBitlogs(
    bitlogs: List<UByte>,
    identifiers: TelemetryIdentifiers,
  ): List<List<UByte>>
}
