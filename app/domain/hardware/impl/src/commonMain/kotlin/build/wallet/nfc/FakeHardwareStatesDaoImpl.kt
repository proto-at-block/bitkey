package build.wallet.nfc

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.map

@BitkeyInject(AppScope::class)
class FakeHardwareStatesDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : FakeHardwareStatesDao {
  override suspend fun setTransactionVerificationEnabled(enabled: Boolean): Result<Unit, DbError> =
    databaseProvider.database()
      .fakeHardwareStatesQueries
      .awaitTransactionWithResult {
        setTransactionVerificationEnabled(enabled)
      }.logFailure {
        "Failed to set Transaction Verification enabled state to $enabled"
      }

  override suspend fun getTransactionVerificationEnabled(): Result<Boolean?, DbError> =
    databaseProvider.database()
      .fakeHardwareStatesQueries
      .getTransactionVerificationEnabled()
      .awaitAsOneOrNullResult()
      .map { entity ->
        entity?.transactionVerificationEnabled
      }.logFailure {
        "Failed to get Transaction Verification enabled state"
      }

  override suspend fun clear(): Result<Unit, DbError> =
    databaseProvider.database()
      .fakeHardwareStatesQueries
      .awaitTransactionWithResult {
        clear()
      }.logFailure {
        "Failed to clear fake hardware states"
      }
}
