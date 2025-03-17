package build.wallet.account.analytics

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.platform.random.UuidGenerator
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class AppInstallationDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val uuidGenerator: UuidGenerator,
) : AppInstallationDao {
  override suspend fun getOrCreateAppInstallation(): Result<AppInstallation, DbError> {
    return databaseProvider.database()
      .appInstallationQueries
      .awaitTransactionWithResult {
        initializeAppInstallationIfAbsent(uuidGenerator.random())

        getAppInstallation()
          .executeAsOne()
          .let {
            AppInstallation(
              // Uppercase for consistency. [W-1156]
              localId = it.id.uppercase(),
              hardwareSerialNumber = it.hardwareSerialNumber
            )
          }
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
