package build.wallet.cloud.store

import com.github.michaelbull.result.Result

interface GoogleAccountRepository {
  suspend fun currentAccount(): Result<GoogleAccount?, GoogleAccountError>
}
