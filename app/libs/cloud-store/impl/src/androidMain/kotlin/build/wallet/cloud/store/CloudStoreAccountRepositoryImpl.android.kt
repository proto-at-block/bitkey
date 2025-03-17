package build.wallet.cloud.store

import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

@BitkeyInject(AppScope::class)
class CloudStoreAccountRepositoryImpl(
  private val googleAccountRepository: GoogleAccountRepository,
) : CloudStoreAccountRepository {
  override suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError> {
    return when (cloudStoreServiceProvider) {
      is GoogleDrive -> googleAccountRepository.currentAccount()
      else -> error("Cloud store service provider $cloudStoreServiceProvider is not supported.")
    }.logFailure { "Error loading current cloud store account." }
  }

  override suspend fun clear(): Result<Unit, Throwable> = Ok(Unit)
}
