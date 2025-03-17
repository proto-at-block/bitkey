package build.wallet.onboarding

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.*
import build.wallet.sqldelight.awaitTransaction
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class OnboardingKeyboxHardwareKeysDaoImpl(
  private val databaseProvider: BitkeyDatabaseProvider,
) : OnboardingKeyboxHardwareKeysDao {
  private suspend fun database() = databaseProvider.database()

  override suspend fun get(): Result<OnboardingKeyboxHardwareKeys?, DbTransactionError> {
    return database().awaitTransactionWithResult {
      onboardingKeyboxHwAuthPublicKeyQueries
        .get()
        .executeAsOneOrNull()
        ?.let { keys ->
          val hwAuthPublicKey = keys.hwAuthPublicKey
          val appGlobalAuthKeyHwSignature = keys.appGlobalAuthKeyHwSignature
          if (hwAuthPublicKey != null) {
            OnboardingKeyboxHardwareKeys(hwAuthPublicKey, appGlobalAuthKeyHwSignature)
          } else {
            logWarn {
              "No hardware auth key and/or app global auth key hw signature found in database."
            }
            null
          }
        }
    }
  }

  override suspend fun set(keys: OnboardingKeyboxHardwareKeys): Result<Unit, DbTransactionError> {
    return database().awaitTransactionWithResult {
      onboardingKeyboxHwAuthPublicKeyQueries.set(
        keys.hwAuthPublicKey,
        keys.appGlobalAuthKeyHwSignature
      )
    }
  }

  override suspend fun clear() {
    database().awaitTransaction {
      onboardingKeyboxHwAuthPublicKeyQueries.clear()
    }
  }
}
