package build.wallet.fwup

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.firmware.FirmwareDeviceInfoDao
import build.wallet.firmware.McuRole
import build.wallet.fwup.FwupMode.Delta
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import okio.ByteString.Companion.encodeUtf8

@Fake
@BitkeyInject(AppScope::class)
class FwupDataDaoFake(
  firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : FwupDataDao {
  private val firmwareDeviceInfoFlow = firmwareDeviceInfoDao.deviceInfo()

  private var lastOfferedUpdateVersion: String? = null
  private val mcuSequenceIds = mutableMapOf<McuRole, UInt>()
  private val mcuFwupData = mutableMapOf<McuRole, McuFwupData>()

  override suspend fun setMcuFwupData(mcuFwupDataList: List<McuFwupData>): Result<Unit, Error> {
    mcuFwupDataList.forEach { data ->
      mcuFwupData[data.mcuRole] = data
    }
    return Ok(Unit)
  }

  override suspend fun getMcuFwupData(mcuRole: McuRole): Result<McuFwupData?, Error> {
    return Ok(mcuFwupData[mcuRole])
  }

  override suspend fun getAllMcuFwupData(): Result<List<McuFwupData>, Error> {
    return Ok(mcuFwupData.values.toList())
  }

  override suspend fun clearAllMcuFwupData(): Result<Unit, Error> {
    mcuFwupData.clear()
    return Ok(Unit)
  }

  override suspend fun clearMcuFwupData(mcuRole: McuRole): Result<Unit, Error> {
    mcuFwupData.remove(mcuRole)
    return Ok(Unit)
  }

  override fun mcuFwupData(): Flow<Result<List<McuFwupData>, Error>> {
    return firmwareDeviceInfoFlow.map { firmwareDeviceInfo ->
      val currentVersion = firmwareDeviceInfo.get()?.version
      if (currentVersion != null) {
        val incrementedVersion = incrementSemver(currentVersion)

        // If we've already offered this incremented version, return empty (up-to-date)
        if (lastOfferedUpdateVersion == incrementedVersion) {
          Ok(emptyList())
        } else {
          lastOfferedUpdateVersion = incrementedVersion
          Ok(listOf(createMockMcuUpdateData(incrementedVersion)))
        }
      } else {
        // No current version, offer a default update
        val defaultVersion = "1.0.1"
        lastOfferedUpdateVersion = defaultVersion
        Ok(listOf(createMockMcuUpdateData(defaultVersion)))
      }
    }
  }

  override suspend fun clear(): Result<Unit, Error> {
    lastOfferedUpdateVersion = null
    return Ok(Unit)
  }

  override suspend fun getMcuSequenceId(mcuRole: McuRole): Result<UInt, Error> {
    return Ok(
      mcuSequenceIds[mcuRole]
        ?: throw NoSuchElementException("No MCU sequence ID found for $mcuRole in the database.")
    )
  }

  override suspend fun setMcuSequenceId(
    mcuRole: McuRole,
    sequenceId: UInt,
  ): Result<Unit, Error> {
    mcuSequenceIds[mcuRole] = sequenceId
    return Ok(Unit)
  }

  override suspend fun clearAllMcuStates(): Result<Unit, Error> {
    mcuSequenceIds.clear()
    return Ok(Unit)
  }

  private fun incrementSemver(version: String): String {
    return try {
      val parts = version.split('.')
      if (parts.size == 3) {
        val major = parts[0].toInt()
        val minor = parts[1].toInt()
        val patch = parts[2].toInt()
        "$major.$minor.${patch + 1}"
      } else {
        "1.0.1"
      }
    } catch (e: NumberFormatException) {
      "1.0.1"
    }
  }

  private fun createMockMcuUpdateData(version: String): McuFwupData {
    return McuFwupData(
      mcuRole = McuRole.CORE,
      mcuName = build.wallet.firmware.McuName.EFR32,
      version = version,
      chunkSize = 0u,
      signatureOffset = 0u,
      appPropertiesOffset = 0u,
      firmware = "firmware".encodeUtf8(),
      signature = "signature".encodeUtf8(),
      fwupMode = Delta
    )
  }
}
