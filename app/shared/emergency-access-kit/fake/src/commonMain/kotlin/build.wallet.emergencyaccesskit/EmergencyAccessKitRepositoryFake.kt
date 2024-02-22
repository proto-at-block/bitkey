package build.wallet.emergencyaccesskit

import build.wallet.cloud.store.CloudStoreAccount
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import okio.ByteString

class EmergencyAccessKitRepositoryFake : EmergencyAccessKitRepository {
  override suspend fun read(
    account: CloudStoreAccount,
  ): Result<EmergencyAccessKitData, EmergencyAccessKitRepositoryError> {
    val fakeEmergencyAccessKitData = EmergencyAccessKitData(ByteString.EMPTY)
    return Ok(fakeEmergencyAccessKitData)
  }

  override suspend fun write(
    account: CloudStoreAccount,
    emergencyAccessKitData: EmergencyAccessKitData,
  ): Result<Unit, EmergencyAccessKitRepositoryError> {
    return Ok(Unit)
  }
}
