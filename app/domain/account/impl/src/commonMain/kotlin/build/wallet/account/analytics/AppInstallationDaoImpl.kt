package build.wallet.account.analytics

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.database.sqldelight.AppInstallationEntity
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.random.UuidGenerator
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapLatest

@BitkeyInject(AppScope::class)
class AppInstallationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val uuidGenerator: UuidGenerator,
) : AppInstallationDao {
  override fun appInstallation(): Flow<Result<AppInstallation?, DbError>> {
    return flow {
      databaseProvider.database()
        .appInstallationQueries
        .getAppInstallation()
        .asFlowOfOneOrNull()
        .distinctUntilChanged()
        .mapLatest { result -> result.map { it?.toDomain() } }
        .collect(::emit)
    }
  }

  override suspend fun getOrCreateAppInstallation(): Result<AppInstallation, DbError> {
    return databaseProvider.database()
      .appInstallationQueries
      .awaitTransactionWithResult {
        initializeAppInstallationIfAbsent(uuidGenerator.random())
        getAppInstallation().executeAsOne().toDomain()
      }
      .logFailure { "Failed to get or create app installation" }
  }

  override suspend fun updateAppInstallationHardwareSerialNumber(
    serialNumber: String,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .appInstallationQueries
      .awaitTransaction {
        initializeAppInstallationIfAbsent(uuidGenerator.random())
        updateHardwareSerialNumber(serialNumber)
      }
      .logFailure { "Failed to update app installation hardware serial number" }
  }
}

private fun AppInstallationEntity.toDomain() =
  AppInstallation(
    localId = id.uppercase(), // Uppercase for consistency. [W-1156]
    hardwareSerialNumber = hardwareSerialNumber
  )
