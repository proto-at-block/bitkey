package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppAuthPublicKeys
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.hardware.HwSpendingKeyProof
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.cloud.backup.csek.SealedSsek
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.W3UpgradeMigrationEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@BitkeyInject(AppScope::class)
class W3UpgradeDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : W3UpgradeDao {
  override fun currentState(): Flow<Result<W3UpgradeMigrationEntity?, DbError>> {
    return flow {
      databaseProvider.database()
        .w3UpgradeMigrationQueries
        .getState()
        .asFlowOfOneOrNull()
        .collect(::emit)
    }
  }

  override suspend fun saveHardwareKey(
    hwKey: HwSpendingPublicKey,
    hwKeyProof: HwSpendingKeyProof?,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        saveHardwareKey(newHardwareKey = hwKey, newHardwareKeyProof = hwKeyProof)
      }
  }

  override suspend fun saveOldDeviceSerial(serial: String): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        saveOldDeviceSerial(serial)
      }
  }

  override suspend fun saveOldHardwareFingerprint(fingerprint: String): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        saveOldHardwareFingerprint(fingerprint)
      }
  }

  override suspend fun saveAppKey(
    appSpendingPublicKey: AppSpendingPublicKey,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        saveAppKey(appSpendingPublicKey)
      }
  }

  override suspend fun saveServerKey(serverKey: F8eSpendingKeyset): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        saveServerKey(serverKey)
      }
  }

  override suspend fun saveKeysetLocalId(keysetLocalId: String): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setKeysetLocalId(keysetLocalId)
      }
  }

  override suspend fun setDescriptorBackupComplete(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setDescriptorBackupCompleted()
      }
  }

  override suspend fun setCloudBackupComplete(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setCloudBackupCompleted()
      }
  }

  override suspend fun setServerKeysetActive(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setServerKeysetActivated()
      }
  }

  override suspend fun setAuthKeyRotationComplete(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setAuthKeyRotationCompleted()
      }
  }

  override suspend fun markResumedFromCloudBackup(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        markResumedFromCloudBackup()
      }
  }

  override suspend fun setDdkBackedUp(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setDdkBackedUp()
      }
  }

  override suspend fun setSweepCompleted(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setSweepCompleted()
      }
  }

  override suspend fun savePendingAuthRotationData(
    newAppAuthKeys: AppAuthPublicKeys,
    hwAuthPublicKey: HwAuthPublicKey,
    hwSignedAccountId: String,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    oldHwAuthPublicKey: HwAuthPublicKey,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        savePendingAuthRotationData(
          pendingAppGlobalAuthKey = newAppAuthKeys.appGlobalAuthPublicKey,
          pendingAppRecoveryAuthKey = newAppAuthKeys.appRecoveryAuthPublicKey,
          pendingAppGlobalAuthKeyHwSignature = newAppAuthKeys.appGlobalAuthKeyHwSignature,
          pendingHwAuthPublicKey = hwAuthPublicKey,
          pendingHwSignedAccountId = hwSignedAccountId,
          preRotationAppGlobalAuthKey = oldAppGlobalAuthKey,
          preRotationHwAuthPublicKey = oldHwAuthPublicKey
        )
      }
  }

  override suspend fun setServerAuthRotationCompleted(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setServerAuthRotationCompleted()
      }
  }

  override suspend fun setTcEndorsementsRegenerated(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setTcEndorsementsRegenerated()
      }
  }

  override suspend fun setSealedSsekForDecryption(
    sealedSsek: SealedSsek?,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        setSealedSsekForDecryption(sealedSsek)
      }
  }

  override suspend fun clearPendingAuthRotationData(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        clearPendingAuthRotationData()
      }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .w3UpgradeMigrationQueries
      .awaitTransaction {
        clearSweepTransactions()
        clear()
      }
  }
}
