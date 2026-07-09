package build.wallet.cloud.backup

import bitkey.account.AccountConfigService
import bitkey.account.isFakeCloudStoreActive
import build.wallet.cloud.store.CloudError
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.cloudStoreAccountRouting
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import com.github.michaelbull.result.Result
import okio.ByteString

@BitkeyInject(AppScope::class)
class CloudBackupStoreDelegate(
  @Impl private val realStore: CloudBackupStore,
  @Fake private val fakeStore: CloudBackupStore,
  private val accountConfigService: AccountConfigService,
) : CloudBackupStore {
  private suspend fun <T> withRoutedStore(
    account: CloudStoreAccount,
    operation: suspend CloudBackupStore.(CloudStoreAccount) -> Result<T, CloudError>,
  ): Result<T, CloudError> {
    val routing = account.cloudStoreAccountRouting(accountConfigService.isFakeCloudStoreActive)
    val store = if (routing.useFakeStore) fakeStore else realStore
    return store.operation(routing.account)
  }

  override suspend fun set(
    account: CloudStoreAccount,
    key: String,
    value: ByteString,
  ): Result<Unit, CloudError> =
    withRoutedStore(account) { routedAccount -> set(routedAccount, key, value) }

  override suspend fun get(
    account: CloudStoreAccount,
    key: String,
  ): Result<ByteString?, CloudError> =
    withRoutedStore(account) { routedAccount -> get(routedAccount, key) }

  override suspend fun remove(
    account: CloudStoreAccount,
    key: String,
  ): Result<Unit, CloudError> =
    withRoutedStore(account) { routedAccount -> remove(routedAccount, key) }

  override suspend fun keys(account: CloudStoreAccount): Result<List<String>, CloudError> =
    withRoutedStore(account) { routedAccount -> keys(routedAccount) }
}
