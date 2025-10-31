package build.wallet.wallet.migration

import build.wallet.bitkey.app.AppSpendingPublicKey
import build.wallet.bitkey.f8e.F8eSpendingKeyset
import build.wallet.bitkey.hardware.HwSpendingPublicKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.PrivateWalletMigrationEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@BitkeyInject(AppScope::class)
class PrivateWalletMigrationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : PrivateWalletMigrationDao {
  override fun currentState(): Flow<Result<PrivateWalletMigrationEntity?, DbError>> {
    return flow {
      databaseProvider.database()
        .privateWalletMigrationQueries
        .getState()
        .asFlowOfOneOrNull()
        .collect(::emit)
    }
  }

  override suspend fun saveHardwareKey(hwKey: HwSpendingPublicKey): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        saveHardwareKey(hwKey)
      }
  }

  override suspend fun saveAppKey(
    appSpendingPublicKey: AppSpendingPublicKey,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        saveAppKey(appSpendingPublicKey)
      }
  }

  override suspend fun saveServerKey(serverKey: F8eSpendingKeyset): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        saveServerKey(serverKey)
      }
  }

  override suspend fun saveKeysetLocalId(keysetLocalId: String): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        setKeysetLocalId(keysetLocalId)
      }
  }

  override suspend fun setDescriptorBackupCompete(): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        setDescriptorBackupCompleted()
      }
  }

  override suspend fun setCloudBackupComplete(): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        setCloudBackupCompleted()
      }
  }

  override suspend fun setServerKeysetActive(): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        setServerKeysetActivated()
      }
  }

  override suspend fun setSweepCompleted(): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        setSweepCompleted()
      }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .privateWalletMigrationQueries
      .awaitTransaction {
        clear()
      }
  }
}
