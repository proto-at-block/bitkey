package build.wallet.fwup

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.FwupDataEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Impl
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfList
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import okio.ByteString.Companion.toByteString

@Impl
@BitkeyInject(AppScope::class)
class FwupDataDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FwupDataDao {
  private suspend fun database() = databaseProvider.database()

  override suspend fun setMcuFwupData(mcuFwupDataList: List<McuFwupData>): Result<Unit, DbError> {
    return database()
      .awaitTransaction {
        mcuFwupDataList.forEach { mcuFwupData ->
          fwupDataQueries.setMcuFwupData(
            mcuRole = mcuFwupData.mcuRole.name,
            mcuName = mcuFwupData.mcuName.name,
            version = mcuFwupData.version,
            chunkSize = mcuFwupData.chunkSize.toLong(),
            signatureOffset = mcuFwupData.signatureOffset.toLong(),
            appPropertiesOffset = mcuFwupData.appPropertiesOffset.toLong(),
            firmware = mcuFwupData.firmware.toByteArray(),
            signature = mcuFwupData.signature.toByteArray(),
            fwupMode = mcuFwupData.fwupMode
          )
        }
      }
      .logFailure { "Failed to set MCU fwup data" }
  }

  override suspend fun getMcuFwupData(mcuRole: McuRole): Result<McuFwupData?, DbError> {
    return database()
      .awaitTransactionWithResult {
        fwupDataQueries.getMcuFwupData(mcuRole.name).executeAsOneOrNull()?.toMcuFwupData()
      }
      .logFailure { "Failed to get MCU fwup data for $mcuRole" }
  }

  override suspend fun getAllMcuFwupData(): Result<List<McuFwupData>, DbError> {
    return database()
      .awaitTransactionWithResult {
        fwupDataQueries.getAllMcuFwupData().executeAsList().map { it.toMcuFwupData() }
      }
      .logFailure { "Failed to get all MCU fwup data" }
  }

  override suspend fun clearAllMcuFwupData(): Result<Unit, DbError> {
    return database()
      .awaitTransaction { fwupDataQueries.clearAllMcuFwupData() }
      .logFailure { "Failed to clear all MCU fwup data" }
  }

  override suspend fun clearMcuFwupData(mcuRole: McuRole): Result<Unit, DbError> {
    return database()
      .awaitTransaction { fwupDataQueries.clearMcuFwupData(mcuRole.name) }
      .logFailure { "Failed to clear MCU fwup data for $mcuRole" }
  }

  override fun mcuFwupData(): Flow<Result<List<McuFwupData>, DbError>> {
    return flow {
      databaseProvider.database()
        .fwupDataQueries
        .getAllMcuFwupData()
        .asFlowOfList()
        .map { result ->
          result
            .map { entities -> entities.map { it.toMcuFwupData() } }
            .logFailure { "Failed to get MCU fwup data" }
        }
        .distinctUntilChanged()
        .collect(::emit)
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return database()
      .awaitTransaction { fwupDataQueries.clear() }
      .logFailure { "Failed to clear fwup data" }
  }

  override suspend fun getMcuSequenceId(mcuRole: McuRole): Result<UInt, DbError> {
    return database()
      .awaitTransactionWithResult {
        fwupDataQueries.getMcuSequenceId(mcuRole.name).executeAsOneOrNull()?.toUInt()
          ?: throw NoSuchElementException("No MCU sequence ID found for $mcuRole in the database.")
      }
  }

  override suspend fun setMcuSequenceId(
    mcuRole: McuRole,
    sequenceId: UInt,
  ): Result<Unit, DbError> {
    return database()
      .awaitTransaction {
        fwupDataQueries.setMcuSequenceId(mcuRole.name, sequenceId.toLong())
      }
      .logFailure { "Failed to set MCU sequence ID for $mcuRole" }
  }

  override suspend fun clearAllMcuStates(): Result<Unit, DbError> {
    return database()
      .awaitTransaction { fwupDataQueries.clearAllMcuStates() }
      .logFailure { "Failed to clear all MCU states" }
  }
}

private fun FwupDataEntity.toMcuFwupData() =
  McuFwupData(
    mcuRole = McuRole.valueOf(mcuRole),
    mcuName = McuName.valueOf(mcuName),
    version = version,
    chunkSize = chunkSize.toUInt(),
    signatureOffset = signatureOffset.toUInt(),
    appPropertiesOffset = appPropertiesOffset.toUInt(),
    firmware = firmware.toByteString(),
    signature = signature.toByteString(),
    fwupMode = fwupMode
  )
