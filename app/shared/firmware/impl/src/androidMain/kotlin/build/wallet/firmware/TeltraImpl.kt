package build.wallet.firmware

import build.wallet.catchingResult
import com.github.michaelbull.result.getOrElse
import build.wallet.rust.firmware.TelemetryIdentifiers as TelemetryIdentifiersCore
import build.wallet.rust.firmware.Teltra as TeltraCore

class TeltraImpl : Teltra {
  override fun translateBitlogs(
    bitlogs: List<UByte>,
    identifiers: TelemetryIdentifiers,
  ): List<List<UByte>> {
    return catchingResult {
      TeltraCore().translateBitlogs(
        bitlogs,
        TelemetryIdentifiersCore(
          serial = identifiers.serial,
          version = identifiers.version,
          swType = identifiers.hwRevisionWithSwType(),
          hwRevision = identifiers.hwRevisionWithoutProduct()
        )
      )
    }.getOrElse {
      emptyList()
    }
  }
}
