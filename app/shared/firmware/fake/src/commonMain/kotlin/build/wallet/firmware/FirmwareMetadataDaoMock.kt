package build.wallet.firmware

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class FirmwareMetadataDaoMock(
  turbine: (String) -> Turbine<Any>,
) : FirmwareMetadataDao {
  val clearCalls = turbine("clear fw metadata calls")
  var getActiveFirmwareMetadataResult = Ok<FirmwareMetadata?>(null)

  override suspend fun setFirmwareMetadata(firmwareMetadata: FirmwareMetadata) {
    getActiveFirmwareMetadataResult = Ok(firmwareMetadata)
  }

  override fun activeFirmwareMetadata(): Flow<Result<FirmwareMetadata?, DbError>> {
    return flow { getActiveFirmwareMetadata() }
  }

  override suspend fun getActiveFirmwareMetadata(): Result<FirmwareMetadata?, DbError> {
    return getActiveFirmwareMetadataResult
  }

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls += Unit
    getActiveFirmwareMetadataResult = Ok(null)
    return Ok(Unit)
  }
}
