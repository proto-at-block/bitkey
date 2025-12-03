package build.wallet.nfc

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToOne
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.crypto.PublicKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.Clock
import kotlin.coroutines.coroutineContext

@BitkeyInject(AppScope::class)
class HardwareProvisionedAppKeyStatusDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val clock: Clock,
) : HardwareProvisionedAppKeyStatusDao {
  override suspend fun recordProvisionedKey(
    hwAuthPubKey: HwAuthPublicKey,
    appAuthPubKey: PublicKey<AppGlobalAuthKey>,
  ): Result<Unit, DbError> {
    return databaseProvider.database()
      .hardwareProvisionedAppKeyStatusQueries
      .awaitTransaction {
        insertHardwareProvisionedAppKeyStatus(
          hwAuthPubKey = hwAuthPubKey,
          appAuthPubKey = appAuthPubKey,
          provisionedAt = clock.now()
        )
      }
      .logFailure { "Failed to record hardware provisioned app key status" }
  }

  override suspend fun isKeyProvisionedForActiveAccount(): Result<Boolean, DbError> {
    return databaseProvider.database()
      .hardwareProvisionedAppKeyStatusQueries
      .awaitTransactionWithResult {
        isKeyProvisionedForActiveAccount()
          .executeAsOne()
      }
      .logFailure { "Failed to check if key is provisioned for active account" }
  }

  override fun isKeyProvisionedForActiveAccountFlow(): Flow<Boolean> {
    return flow {
      databaseProvider.database()
        .hardwareProvisionedAppKeyStatusQueries
        .isKeyProvisionedForActiveAccount()
        .asFlow()
        .mapToOne(coroutineContext)
        .collect(::emit)
    }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .hardwareProvisionedAppKeyStatusQueries
      .awaitTransaction {
        clear()
      }
      .logFailure { "Failed to clear hardware provisioned app key status" }
  }
}
