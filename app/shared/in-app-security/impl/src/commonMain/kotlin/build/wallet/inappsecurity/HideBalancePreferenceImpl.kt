package build.wallet.inappsecurity

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted.Companion.Lazily
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class HideBalancePreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  appCoroutineScope: CoroutineScope,
) : HideBalancePreference {
  private val db by lazy {
    databaseProvider.database()
  }

  override val isEnabled: StateFlow<Boolean> =
    db.hideBalancePreferenceQueries
      .getHideBalancePeference()
      .asFlowOfOneOrNull()
      .map { it.get()?.enabled ?: false }
      .stateIn(appCoroutineScope, Lazily, false)

  override suspend fun get(): Result<Boolean, DbError> {
    return db.hideBalancePreferenceQueries
      .getHideBalancePeference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get Lightning Preference Entity" }
      .map { it?.enabled ?: false } // if there is no preference set we assume false
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    return db.hideBalancePreferenceQueries
      .awaitTransactionWithResult {
        setHideBalancePreference(enabled)
      }
  }
}
