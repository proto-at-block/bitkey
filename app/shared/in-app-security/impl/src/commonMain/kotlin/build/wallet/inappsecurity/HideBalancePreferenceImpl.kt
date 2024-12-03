package build.wallet.inappsecurity

import build.wallet.analytics.events.EventTracker
import build.wallet.analytics.v1.Action
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import com.github.michaelbull.result.onSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly

class HideBalancePreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
  private val eventTracker: EventTracker,
  appCoroutineScope: CoroutineScope,
) : HideBalancePreference {
  override val isEnabled: StateFlow<Boolean> =
    flow {
      databaseProvider.database()
        .hideBalancePreferenceQueries
        .getHideBalancePeference()
        .asFlowOfOneOrNull()
        .map { it.get()?.enabled ?: false }
        .collect(::emit)
    }
      .stateIn(appCoroutineScope, Eagerly, false)

  override suspend fun get(): Result<Boolean, DbError> {
    return databaseProvider.database()
      .hideBalancePreferenceQueries
      .getHideBalancePeference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get Lightning Preference Entity" }
      .map { it?.enabled ?: false } // if there is no preference set we assume false
  }

  override suspend fun set(enabled: Boolean): Result<Unit, DbError> {
    return databaseProvider.database()
      .hideBalancePreferenceQueries
      .awaitTransactionWithResult {
        setHideBalancePreference(enabled)
      }.onSuccess {
        if (enabled) {
          eventTracker.track(Action.ACTION_APP_HIDE_BALANCE_BY_DEFAULT_ENABLED)
        } else {
          eventTracker.track(Action.ACTION_APP_HIDE_BALANCE_BY_DEFAULT_DISABLED)
        }
      }
  }

  override suspend fun clear(): Result<Unit, DbError> {
    return databaseProvider.database()
      .hideBalancePreferenceQueries
      .awaitTransactionWithResult {
        clear()
      }
  }
}
