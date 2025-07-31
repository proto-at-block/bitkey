package build.wallet.cloud.store

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class CloudStoreAccountRepositoryMock : CloudStoreAccountRepository, WritableCloudStoreAccountRepository {
  var currentAccountResult: Result<CloudStoreAccount?, CloudStoreAccountError> = Ok(null)

  fun reset() {
    currentAccountResult = Ok(null)
  }

  override suspend fun set(account: CloudStoreAccount): Result<Unit, CloudStoreAccountError> {
    currentAccountResult = Ok(account)
    return Ok(Unit)
  }

  override suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError> = currentAccountResult

  override suspend fun clear(): Result<Unit, Error> {
    currentAccountResult = Ok(null)
    return Ok(Unit)
  }
}
