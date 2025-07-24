package build.wallet.fwup

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.firmware.FirmwareDeviceInfoDao
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

  override fun fwupData(): Flow<Result<FwupData?, Error>> {
    return firmwareDeviceInfoFlow.map { firmwareDeviceInfo ->
      val currentVersion = firmwareDeviceInfo.get()?.version
      if (currentVersion != null) {
        val incrementedVersion = incrementSemver(currentVersion)

        // If we've already offered this incremented version, return null (up-to-date)
        if (lastOfferedUpdateVersion == incrementedVersion) {
          Ok(null)
        } else {
          lastOfferedUpdateVersion = incrementedVersion
          Ok(createMockUpdateData(incrementedVersion))
        }
      } else {
        // No current version, offer a default update
        val defaultVersion = "1.0.1"
        lastOfferedUpdateVersion = defaultVersion
        Ok(createMockUpdateData(defaultVersion))
      }
    }
  }

  override suspend fun setFwupData(fwupData: FwupData): Result<Unit, Error> {
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    lastOfferedUpdateVersion = null
    return Ok(Unit)
  }

  override suspend fun setSequenceId(sequenceId: UInt): Result<Unit, Error> {
    return Ok(Unit)
  }

  override suspend fun getSequenceId(): Result<UInt, Error> {
    return Ok(0u)
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

  private fun createMockUpdateData(version: String): FwupData {
    return FwupData(
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
