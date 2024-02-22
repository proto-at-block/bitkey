package build.wallet.firmware

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FirmwareMetadataEntity
import build.wallet.db.DbError
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.A
import build.wallet.firmware.FirmwareMetadata.FirmwareSlot.B
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import build.wallet.unwrapLoadedValue
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Instant
import okio.ByteString.Companion.toByteString

class FirmwareMetadataDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FirmwareMetadataDao {
  override suspend fun setFirmwareMetadata(firmwareMetadata: FirmwareMetadata) {
    databaseProvider.database().awaitTransaction {
      firmwareMetadataQueries.setActiveFirmwareMetadata(
        activeSlot = firmwareMetadata.activeSlot.name,
        gitId = firmwareMetadata.gitId,
        gitBranch = firmwareMetadata.gitBranch,
        version = firmwareMetadata.version,
        build = firmwareMetadata.build,
        timestamp = firmwareMetadata.timestamp.epochSeconds,
        hash = firmwareMetadata.hash.toByteArray(),
        hwRevision = firmwareMetadata.hwRevision
      )
    }
  }

  override fun activeFirmwareMetadata(): Flow<Result<FirmwareMetadata?, DbError>> {
    return databaseProvider.database()
      .firmwareMetadataQueries.getActiveFirmwareMetadata()
      .asFlowOfOneOrNull()
      .unwrapLoadedValue()
      .map { result ->
        result
          .map { firmwareMetadataEntity ->
            firmwareMetadataEntity?.toFirmwareMetadata()
          }
      }
      .distinctUntilChanged()
  }

  override suspend fun getActiveFirmwareMetadata(): Result<FirmwareMetadata?, DbError> {
    return activeFirmwareMetadata().first()
  }

  override suspend fun clear(): Result<Unit, Error> {
    return databaseProvider.database().awaitTransaction {
      firmwareMetadataQueries.clear()
    }
  }
}

private fun FirmwareMetadataEntity.toFirmwareMetadata(): FirmwareMetadata {
  return FirmwareMetadata(
    activeSlot =
      if (activeSlot == "A") {
        A
      } else {
        B
      },
    gitId = gitId,
    gitBranch = gitBranch,
    version = version,
    build = build,
    timestamp = Instant.fromEpochSeconds(timestamp),
    hash = hash.toByteString(),
    hwRevision = hwRevision
  )
}
