package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.database.sqldelight.PrivateWalletMigrationEntity
import build.wallet.db.DbError
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class PrivateWalletMigrationDaoFake : PrivateWalletMigrationDao {
  val state: MutableStateFlow<Result<PrivateWalletMigrationEntity?, DbError>> = MutableStateFlow(Ok(null))

  override fun currentState(): Flow<Result<PrivateWalletMigrationEntity?, DbError>> {
    return state
  }

  override suspend fun saveHardwareKey(hwKey: HwSpendingPublicKey): Result<Unit, DbError> {
    state.value = Ok(
      PrivateWalletMigrationEntity(
        rowId = 0,
        newHardwareKey = hwKey,
        newAppKey = null,
        newServerKey = null,
        keysetLocalId = null,
        sweepCompleted = false,
        descriptorBackupCompleted = false,
        cloudBackupCompleted = false,
        serverKeysetActivated = false
      )
    )

    return Ok(Unit)
  }

  override suspend fun saveAppKey(
    appSpendingPublicKey: AppSpendingPublicKey,
  ): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        newAppKey = appSpendingPublicKey
      )
    )
    return Ok(Unit)
  }

  override suspend fun saveServerKey(serverKey: F8eSpendingKeyset): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        newServerKey = serverKey
      )
    )
    return Ok(Unit)
  }

  override suspend fun saveKeysetLocalId(keysetLocalId: String): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        keysetLocalId = keysetLocalId
      )
    )
    return Ok(Unit)
  }

  override suspend fun setDescriptorBackupComplete(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        descriptorBackupCompleted = true
      )
    )
    return Ok(Unit)
  }

  override suspend fun setCloudBackupComplete(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        cloudBackupCompleted = true
      )
    )
    return Ok(Unit)
  }

  override suspend fun setServerKeysetActive(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        serverKeysetActivated = true
      )
    )
    return Ok(Unit)
  }

  override suspend fun setSweepCompleted(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        sweepCompleted = true
      )
    )
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    state.value = Ok(null)
    return Ok(Unit)
  }
}
