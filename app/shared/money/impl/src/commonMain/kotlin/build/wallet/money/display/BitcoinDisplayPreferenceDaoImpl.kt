package build.wallet.money.display

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitTransaction
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapOr
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

class BitcoinDisplayPreferenceDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : BitcoinDisplayPreferenceDao {
  private val database = databaseProvider.database()

  override fun bitcoinDisplayPreference(): Flow<BitcoinDisplayUnit?> {
    return database.bitcoinDisplayPreferenceQueries.bitcoinDisplayPreference().asFlowOfOneOrNull()
      .map { result ->
        result
          .logFailure { "Failed to read bitcoin display unit preference" }
          .mapOr(null) { entity ->
            entity?.displayUnit
          }
      }
      .distinctUntilChanged()
  }

  override suspend fun setBitcoinDisplayPreference(
    unit: BitcoinDisplayUnit,
  ): Result<Unit, DbError> {
    return database
      .awaitTransaction { bitcoinDisplayPreferenceQueries.setBitcoinDisplayPreference(unit) }
      .logFailure { "Failed to set bitcoin display unit preference" }
  }

  override suspend fun clear() =
    database
      .awaitTransaction { bitcoinDisplayPreferenceQueries.clear() }
      .logFailure { "Failed to clear bitcoin display unit preference" }
}
