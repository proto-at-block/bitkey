package build.wallet.bitcoin.lightning

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.logging.logFailure
import build.wallet.sqldelight.awaitAsOneOrNullResult
import com.github.michaelbull.result.get
import com.github.michaelbull.result.map

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
}
