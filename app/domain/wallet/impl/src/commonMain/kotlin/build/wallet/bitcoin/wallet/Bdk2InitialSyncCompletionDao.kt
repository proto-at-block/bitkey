package build.wallet.bitcoin.wallet

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.store.KeyValueStoreFactory
import com.github.michaelbull.result.Result
import com.russhwolf.settings.coroutines.SuspendSettings

interface Bdk2InitialSyncCompletionDao {
  suspend fun isComplete(walletIdentifier: String): Result<Boolean, Throwable>

  suspend fun markComplete(walletIdentifier: String): Result<Unit, Throwable>
}

@BitkeyInject(AppScope::class)
class Bdk2InitialSyncCompletionDaoImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : Bdk2InitialSyncCompletionDao {
  override suspend fun isComplete(walletIdentifier: String): Result<Boolean, Throwable> =
    catchingResult {
      store().getBoolean(completedKey(walletIdentifier), defaultValue = false)
    }

  override suspend fun markComplete(walletIdentifier: String): Result<Unit, Throwable> =
    catchingResult {
      store().putBoolean(completedKey(walletIdentifier), value = true)
    }

  private suspend fun store(): SuspendSettings =
    keyValueStoreFactory.getOrCreate(STORE_NAME)

  private fun completedKey(walletIdentifier: String): String =
    "completed-$walletIdentifier"

  private companion object {
    const val STORE_NAME = "Bdk2InitialSyncCompletion"
  }
}
