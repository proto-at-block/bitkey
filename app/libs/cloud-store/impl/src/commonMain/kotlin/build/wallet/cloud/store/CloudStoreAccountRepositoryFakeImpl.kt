package build.wallet.cloud.store

import build.wallet.cloud.store.CloudStoreAccountFake.Companion.MockCloudAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.store.KeyValueStoreFactory
import build.wallet.store.clearWithResult
import build.wallet.store.putStringWithResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.russhwolf.settings.ExperimentalSettingsApi

@Fake
@BitkeyInject(AppScope::class)
class CloudStoreAccountRepositoryFakeImpl(
  private val keyValueStoreFactory: KeyValueStoreFactory,
) : CloudStoreAccountRepository, WritableCloudStoreAccountRepository {
  @OptIn(ExperimentalSettingsApi::class)
  private suspend fun store() = keyValueStoreFactory.getOrCreate(STORE_NAME)

  @OptIn(ExperimentalSettingsApi::class)
  override suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError> {
    val identifier = store().getStringOrNull(KEY_CURRENT_ACCOUNT_ID)
      ?: MockCloudAccount.identifier
    return Ok(CloudStoreAccountFake(identifier))
  }

  @OptIn(ExperimentalSettingsApi::class)
  override suspend fun set(account: CloudStoreAccount): Result<Unit, CloudStoreAccountError> {
    val fakeAccount = account as? CloudStoreAccountFake
      ?: return Err(CloudStoreAccountError())

    return store()
      .putStringWithResult(KEY_CURRENT_ACCOUNT_ID, fakeAccount.identifier)
      .mapError { CloudStoreAccountError() }
  }

  @OptIn(ExperimentalSettingsApi::class)
  override suspend fun clear(): Result<Unit, Throwable> {
    return store().clearWithResult()
  }

  private companion object {
    const val STORE_NAME = "CloudStoreAccountFakeImpl"
    const val KEY_CURRENT_ACCOUNT_ID = "current-account-id"
  }
}
