package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.database.sqldelight.W3UpgradeMigrationEntity
import build.wallet.db.DbError
import build.wallet.db.DbTransactionError
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class W3UpgradeDaoFake : W3UpgradeDao {
  val state: MutableStateFlow<Result<W3UpgradeMigrationEntity?, DbError>> =
    MutableStateFlow(Ok(null))
  var shouldFailSetDescriptorBackupComplete: Boolean = false

  private fun defaultEntity() =
    W3UpgradeMigrationEntity(
      rowId = 0,
      newHardwareKey = null,
      newAppKey = null,
      newServerKey = null,
      keysetLocalId = null,
      sweepCompleted = false,
      descriptorBackupCompleted = false,
      cloudBackupCompleted = false,
      serverKeysetActivated = false,
      authKeyRotationCompleted = false,
      oldDeviceSerial = null,
      ddkBackedUp = false,
      oldHardwareFingerprint = null,
      pendingAppGlobalAuthKey = null,
      pendingAppRecoveryAuthKey = null,
      pendingAppGlobalAuthKeyHwSignature = null,
      pendingHwAuthPublicKey = null,
      pendingHwSignedAccountId = null,
      serverAuthRotationCompleted = false,
      preRotationAppGlobalAuthKey = null,
      preRotationHwAuthPublicKey = null,
      tcEndorsementsRegenerated = false,
      sealedSsekForDecryption = null,
      resumedFromCloudBackup = false
    )

  override fun currentState(): Flow<Result<W3UpgradeMigrationEntity?, DbError>> {
    return state
  }

  override suspend fun saveHardwareKey(hwKey: HwSpendingPublicKey): Result<Unit, DbError> {
    upsert { it.copy(newHardwareKey = hwKey) }
    return Ok(Unit)
  }

  override suspend fun saveOldDeviceSerial(serial: String): Result<Unit, DbError> {
    upsert { it.copy(oldDeviceSerial = serial) }
    return Ok(Unit)
  }

  override suspend fun saveOldHardwareFingerprint(fingerprint: String): Result<Unit, DbError> {
    upsert { it.copy(oldHardwareFingerprint = fingerprint) }
    return Ok(Unit)
  }

  override suspend fun saveAppKey(
    appSpendingPublicKey: AppSpendingPublicKey,
  ): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(newAppKey = appSpendingPublicKey)
    )
    return Ok(Unit)
  }

  override suspend fun saveServerKey(serverKey: F8eSpendingKeyset): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(newServerKey = serverKey)
    )
    return Ok(Unit)
  }

  override suspend fun saveKeysetLocalId(keysetLocalId: String): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(keysetLocalId = keysetLocalId)
    )
    return Ok(Unit)
  }

  override suspend fun setDescriptorBackupComplete(): Result<Unit, DbError> {
    if (shouldFailSetDescriptorBackupComplete) {
      return Err(DbTransactionError(Exception("Failed to set descriptor backup complete")))
    }
    state.value = Ok(
      state.value.value!!.copy(descriptorBackupCompleted = true)
    )
    return Ok(Unit)
  }

  override suspend fun setCloudBackupComplete(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(cloudBackupCompleted = true)
    )
    return Ok(Unit)
  }

  override suspend fun setServerKeysetActive(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(serverKeysetActivated = true)
    )
    return Ok(Unit)
  }

  override suspend fun setAuthKeyRotationComplete(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(authKeyRotationCompleted = true)
    )
    return Ok(Unit)
  }

  override suspend fun markResumedFromCloudBackup(): Result<Unit, DbError> {
    upsert { it.copy(resumedFromCloudBackup = true) }
    return Ok(Unit)
  }

  override suspend fun setDdkBackedUp(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(ddkBackedUp = true)
    )
    return Ok(Unit)
  }

  override suspend fun setSweepCompleted(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(sweepCompleted = true)
    )
    return Ok(Unit)
  }

  override suspend fun savePendingAuthRotationData(
    newAppAuthKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    oldHwAuthPublicKey: HwAuthPublicKey,
  ): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        pendingAppGlobalAuthKey = newAppAuthKeys.appGlobalAuthPublicKey,
        pendingAppRecoveryAuthKey = newAppAuthKeys.appRecoveryAuthPublicKey,
        pendingAppGlobalAuthKeyHwSignature = newAppAuthKeys.appGlobalAuthKeyHwSignature,
        pendingHwAuthPublicKey = hwAuthPublicKey,
        pendingHwSignedAccountId = hwSignedAccountId,
        preRotationAppGlobalAuthKey = oldAppGlobalAuthKey,
        preRotationHwAuthPublicKey = oldHwAuthPublicKey
      )
    )
    return Ok(Unit)
  }

  override suspend fun setServerAuthRotationCompleted(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(serverAuthRotationCompleted = true)
    )
    return Ok(Unit)
  }

  override suspend fun setTcEndorsementsRegenerated(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(tcEndorsementsRegenerated = true)
    )
    return Ok(Unit)
  }

  override suspend fun setSealedSsekForDecryption(sealedSsek: SealedSsek?): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(sealedSsekForDecryption = sealedSsek)
    )
    return Ok(Unit)
  }

  override suspend fun clearPendingAuthRotationData(): Result<Unit, DbError> {
    state.value = Ok(
      state.value.value!!.copy(
        pendingAppGlobalAuthKey = null,
        pendingAppRecoveryAuthKey = null,
        pendingAppGlobalAuthKeyHwSignature = null,
        pendingHwAuthPublicKey = null,
        pendingHwSignedAccountId = null,
        serverAuthRotationCompleted = false,
        preRotationAppGlobalAuthKey = null,
        preRotationHwAuthPublicKey = null,
        tcEndorsementsRegenerated = false
      )
    )
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, DbError> {
    state.value = Ok(null)
    shouldFailSetDescriptorBackupComplete = false
    return Ok(Unit)
  }

  fun reset() {
    state.value = Ok(null)
    shouldFailSetDescriptorBackupComplete = false
  }

  private fun upsert(transform: (W3UpgradeMigrationEntity) -> W3UpgradeMigrationEntity) {
    state.value = Ok(transform(state.value.value ?: defaultEntity()))
  }
}
