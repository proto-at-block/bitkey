package build.wallet.onboarding

import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.sqldelight.awaitTransactionWithResult

class OnboardingKeyboxHwAuthPublicKeyDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : OnboardingKeyboxHwAuthPublicKeyDao {
  private val database = databaseProvider.database()
  private val queries = database.onboardingKeyboxHwAuthPublicKeyQueries

  override suspend fun get() =
    database.awaitTransactionWithResult { queries.get().executeAsOneOrNull()?.hwAuthPublicKey }

  override suspend fun set(publicKey: HwAuthPublicKey) =
    database.awaitTransactionWithResult { queries.set(publicKey) }

  override suspend fun clear() = queries.clear()
}
