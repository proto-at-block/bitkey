package build.wallet.bitcoin.lightning

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.logging.logFailure
import build.wallet.sqldelight.asFlowOfOneOrNull
import build.wallet.sqldelight.awaitAsOneOrNullResult
import build.wallet.unwrapLoadedValue
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class LightningPreferenceImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : LightningPreference {
  private val db by lazy {
    databaseProvider.database()
  }

  override suspend fun get(): Boolean {
    return db.lightningPreferenceQueries.getLightningPeference()
      .awaitAsOneOrNullResult()
      .logFailure { "Unable to get Lightning Preference Entity" }
      .map { it?.enabled }
      .get()
      ?: false // Return false as default or on Error.
  }

  override suspend fun set(enabled: Boolean) {
    db.lightningPreferenceQueries.setLightningPreference(enabled)
  }

  override fun isEnabled(): Flow<Boolean> {
    return db.lightningPreferenceQueries
      .getLightningPeference()
      .asFlowOfOneOrNull()
      .unwrapLoadedValue()
      .map { it.get()?.enabled ?: false }
  }
}
