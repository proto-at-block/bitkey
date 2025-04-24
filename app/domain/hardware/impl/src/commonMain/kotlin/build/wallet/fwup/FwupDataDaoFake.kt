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
  private val firmwareDeviceInfoDao: FirmwareDeviceInfoDao,
) : FwupDataDao {
  private val firmwareDeviceInfoFlow = firmwareDeviceInfoDao.deviceInfo()

  override fun fwupData(): Flow<Result<FwupData?, Error>> {
    return firmwareDeviceInfoFlow.map { firmwareDeviceInfo ->
      if (firmwareDeviceInfo.get()?.version == FwupDataMockUpdate.version) {
        Ok(null)
      } else {
        Ok(FwupDataMockUpdate)
      }
    }
  }

  override suspend fun setFwupData(fwupData: FwupData): Result<Unit, Error> {
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    return Ok(Unit)
  }

  override suspend fun setSequenceId(sequenceId: UInt): Result<Unit, Error> {
    return Ok(Unit)
  }

  override suspend fun getSequenceId(): Result<UInt, Error> {
    return Ok(0u)
  }
}

val FwupDataMockUpdate =
  FwupData(
    version = "fake-update",
    chunkSize = 0u,
    signatureOffset = 0u,
    appPropertiesOffset = 0u,
    firmware = "firmware".encodeUtf8(),
    signature = "signature".encodeUtf8(),
    fwupMode = Delta
  )
