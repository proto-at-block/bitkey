package build.wallet.cloud.store

import com.github.michaelbull.result.Result

interface CloudStoreAccountRepository {
  /**
   * Look for currently logged in cloud store account based on the cloud storage type.
   */
  suspend fun currentAccount(
    cloudStoreServiceProvider: CloudStoreServiceProvider,
  ): Result<CloudStoreAccount?, CloudStoreAccountError>

  /**
   * Clears cloud store account state.
   *
   * Currently mostly used for testing purposes.
   */
  suspend fun clear(): Result<Unit, Throwable>
}
