package build.wallet.cloud.store

import build.wallet.logging.logFailure
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.clearWithResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

actual class CloudStoreAccountRepositoryImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : CloudStoreAccountRepository, WritableCloudStoreAccountRepository {
  private suspend fun store() = keyValueStoreFactory.getOrCreate(STORE_NAME)

  actual override suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError> {
    return store()
      .getStringOrNull(KEY_CURRENT_ACCOUNT_ID)
      ?.let(::CloudStoreAccountFake)
      .let(::Ok)
      .logFailure { "Error loading current cloud store account." }
  }

  actual override suspend fun clear(): Result<Unit, Throwable> {
    return store().clearWithResult()
  }

  private companion object {
    const val STORE_NAME = "CloudStoreAccountFake"
    const val KEY_CURRENT_ACCOUNT_ID = "current-account-id"
  }

  override suspend fun set(account: CloudStoreAccount): Result<Unit, CloudStoreAccountError> {
    return store()
      .putString(KEY_CURRENT_ACCOUNT_ID, (account as CloudStoreAccountFake).identifier)
      .let(::Ok)
  }
}
