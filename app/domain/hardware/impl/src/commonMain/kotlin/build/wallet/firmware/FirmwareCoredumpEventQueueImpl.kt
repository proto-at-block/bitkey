package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import okio.ByteString.Companion.toByteString

@BitkeyInject(AppScope::class)
class FirmwareCoredumpEventQueueImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FirmwareCoredumpEventQueue {
  override suspend fun append(item: FirmwareCoredump): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      firmwareCoredumpsQueries.append(
        coredump = item.coredump.toByteArray(),
        serial = item.identifiers.serial,
        swType = item.identifiers.swType,
        swVersion = item.identifiers.version,
        hwVersion = item.identifiers.hwRevision,
        mcuInfo = item.identifiers.mcuInfo
      )
    }
  }

  override suspend fun take(num: Int): Result<List<FirmwareCoredump>, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database().firmwareCoredumpsQueries.take(num.toLong())
      .awaitAsListResult()
      .map { items ->
        items.map { item ->
          FirmwareCoredump(
            item.coredump.toByteString(),
            TelemetryIdentifiers(
              serial = item.serial,
              version = item.swVersion,
              swType = item.swType,
              hwRevision = item.hwVersion,
              mcuInfo = item.mcuInfo
            )
          )
        }
      }
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database().firmwareCoredumpsQueries.awaitTransaction {
      removeFirst(num.toLong())
    }
  }
}
