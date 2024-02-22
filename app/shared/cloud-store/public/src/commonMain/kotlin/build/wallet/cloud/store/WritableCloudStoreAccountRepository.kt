package build.wallet.cloud.store

import com.github.michaelbull.result.Result

/**
 * JVM-specific interface for writing cloud store account credentials,
 * mostly used for testing.
 */
interface WritableCloudStoreAccountRepository : CloudStoreAccountRepository {
  suspend fun set(account: CloudStoreAccount): Result<Unit, CloudStoreAccountError>
}
