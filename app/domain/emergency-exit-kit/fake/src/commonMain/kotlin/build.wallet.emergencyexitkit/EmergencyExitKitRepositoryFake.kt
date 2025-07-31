package build.wallet.emergencyexitkit

import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class EmergencyExitKitRepositoryFake : EmergencyExitKitRepository {
  private val eekDataMap = mutableMapOf<CloudStoreAccount, EmergencyExitKitData>()
  var readError: Error? = null

  override suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyExitKitData, EmergencyExitKitRepositoryError> {
    readError?.let { return Err(EmergencyExitKitRepositoryError.UnrectifiableCloudError(it)) }

    val data = eekDataMap[account]
    return data?.let { Ok(it) } ?: Ok(EmergencyExitKitData(ByteString.EMPTY))
  }

  override suspend fun write(
    account: CloudStoreAccount,
    emergencyExitKitData: EmergencyExitKitData,
  ): Result<Unit, EmergencyExitKitRepositoryError> {
    eekDataMap[account] = emergencyExitKitData
    return Ok(Unit)
  }

  fun setEekData(
    account: CloudStoreAccount,
    data: EmergencyExitKitData,
  ) {
    eekDataMap[account] = data
  }

  fun reset() {
    eekDataMap.clear()
    readError = null
  }
}
