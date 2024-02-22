package build.wallet.firmware

import build.wallet.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import build.wallet.core.TelemetryIdentifiers as TelemetryIdentifiersCore
import build.wallet.core.Teltra as TeltraCore

class TeltraImpl : Teltra {
  override fun translateBitlogs(
    bitlogs: List<UByte>,
    identifiers: TelemetryIdentifiers,
  ): List<List<UByte>> {
    return Result.catching {
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
