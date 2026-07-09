package build.wallet.cloud.store

import bitkey.account.AccountConfigService
import bitkey.account.isFakeCloudStoreActive
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.di.Fake
import build.wallet.di.Impl
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class CloudStoreAccountRepositoryDelegate(
  @Impl private val realRepository: CloudStoreAccountRepository,
  @Fake private val fakeRepository: CloudStoreAccountRepository,
  private val accountConfigService: AccountConfigService,
) : CloudStoreAccountRepository {
  private val delegate: CloudStoreAccountRepository
    get() = if (accountConfigService.isFakeCloudStoreActive) {
      fakeRepository
    } else {
      realRepository
    }

  override suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError> =
    delegate.currentAccount(cloudStoreServiceProvider)

  override suspend fun clear(): Result<Unit, Throwable> = delegate.clear()
}
