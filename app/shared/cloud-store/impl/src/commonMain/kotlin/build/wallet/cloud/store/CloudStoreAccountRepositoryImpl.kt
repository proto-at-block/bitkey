package build.wallet.cloud.store

import com.github.michaelbull.result.Result

expect class CloudStoreAccountRepositoryImpl : CloudStoreAccountRepository {
  override suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError>

  override suspend fun clear(): Result<Unit, Throwable>
}
