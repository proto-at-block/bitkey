package build.wallet.emergencyexitkit

import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class EmergencyExitKitRepositoryFake : EmergencyExitKitRepository {
  override suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyExitKitData, EmergencyExitKitRepositoryError> {
    val fakeEmergencyExitKitData = EmergencyExitKitData(ByteString.EMPTY)
    return Ok(fakeEmergencyExitKitData)
  }

  override suspend fun write(
    account: CloudStoreAccount,
    emergencyExitKitData: EmergencyExitKitData,
  ): Result<Unit, EmergencyExitKitRepositoryError> {
    return Ok(Unit)
  }
}
