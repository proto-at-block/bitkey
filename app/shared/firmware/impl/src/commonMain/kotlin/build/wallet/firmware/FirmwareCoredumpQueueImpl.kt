package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.queueprocessor.Queue
import build.wallet.sqldelight.awaitAsListResult
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import okio.ByteString.Companion.toByteString

class FirmwareCoredumpQueueImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : Queue<FirmwareCoredump> {
  override suspend fun append(item: FirmwareCoredump): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      databaseProvider.database().firmwareCoredumpsQueries.append(
        coredump = item.coredump.toByteArray(),
        serial = item.identifiers.serial,
        swType = item.identifiers.swType,
        swVersion = item.identifiers.version,
        hwVersion = item.identifiers.hwRevision
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
              hwRevision = item.hwVersion
            )
          )
        }
      }
  }

  override suspend fun removeFirst(num: Int): Result<Unit, Error> {
    require(num >= 0) { "Requested element count $num is less than zero." }

    return databaseProvider.database().firmwareCoredumpsQueries.awaitTransaction {
      databaseProvider.database().firmwareCoredumpsQueries.removeFirst(num.toLong())
    }
  }
}
