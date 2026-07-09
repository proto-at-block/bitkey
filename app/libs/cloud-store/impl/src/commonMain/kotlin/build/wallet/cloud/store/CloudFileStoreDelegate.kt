package build.wallet.cloud.store

import bitkey.account.AccountConfigService
import bitkey.account.isFakeCloudStoreActive
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import build.wallet.platform.data.MimeType
import okio.ByteString

@BitkeyInject(AppScope::class)
class CloudFileStoreDelegate(
  @Impl private val realStore: CloudFileStore,
  @Fake private val fakeStore: CloudFileStore,
  private val accountConfigService: AccountConfigService,
) : CloudFileStore {
  private suspend fun <T : Any> withRoutedStore(
    account: CloudStoreAccount,
    operation: suspend CloudFileStore.(CloudStoreAccount) -> CloudFileStoreResult<T>,
  ): CloudFileStoreResult<T> {
    val routing = account.cloudStoreAccountRouting(accountConfigService.isFakeCloudStoreActive)
    val store = if (routing.useFakeStore) fakeStore else realStore
    return store.operation(routing.account)
  }

  override suspend fun exists(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Boolean> =
    withRoutedStore(account) { routedAccount -> exists(routedAccount, fileName) }

  override suspend fun read(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<ByteString> =
    withRoutedStore(account) { routedAccount -> read(routedAccount, fileName) }

  override suspend fun remove(
    account: CloudStoreAccount,
    fileName: String,
  ): CloudFileStoreResult<Unit> =
    withRoutedStore(account) { routedAccount -> remove(routedAccount, fileName) }

  override suspend fun write(
    account: CloudStoreAccount,
    bytes: ByteString,
    fileName: String,
    mimeType: MimeType,
  ): CloudFileStoreResult<Unit> =
    withRoutedStore(account) { routedAccount ->
      write(routedAccount, bytes, fileName, mimeType)
    }
}
