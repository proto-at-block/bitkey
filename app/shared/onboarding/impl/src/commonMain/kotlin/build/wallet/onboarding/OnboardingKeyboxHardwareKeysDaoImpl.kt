package build.wallet.onboarding

import build.wallet.database.BitkeyDatabaseProvider
import build.wallet.db.DbTransactionError
import build.wallet.logging.LogLevel
import build.wallet.logging.log
import build.wallet.sqldelight.awaitTransactionWithResult
import com.github.michaelbull.result.Result

class OnboardingKeyboxHardwareKeysDaoImpl(
  databaseProvider: BitkeyDatabaseProvider,
) : OnboardingKeyboxHardwareKeysDao {
  private val database = databaseProvider.database()
  private val queries = database.onboardingKeyboxHwAuthPublicKeyQueries

  override suspend fun get(): Result<OnboardingKeyboxHardwareKeys?, DbTransactionError> {
    return database.awaitTransactionWithResult {
      queries.get().executeAsOneOrNull()?.let { keys ->
        val hwAuthPublicKey = keys.hwAuthPublicKey
        val appGlobalAuthKeyHwSignature = keys.appGlobalAuthKeyHwSignature
        if (hwAuthPublicKey != null) {
          OnboardingKeyboxHardwareKeys(hwAuthPublicKey, appGlobalAuthKeyHwSignature)
        } else {
          log(LogLevel.Warn) {
            "No hardware auth key and/or app global auth key hw signature found in database."
          }
          null
        }
      }
    }
  }

  override suspend fun set(keys: OnboardingKeyboxHardwareKeys): Result<Unit, DbTransactionError> {
    return database.awaitTransactionWithResult {
      queries.set(
        keys.hwAuthPublicKey,
        keys.appGlobalAuthKeyHwSignature
      )
    }
  }

  override suspend fun clear() = queries.clear()
}
