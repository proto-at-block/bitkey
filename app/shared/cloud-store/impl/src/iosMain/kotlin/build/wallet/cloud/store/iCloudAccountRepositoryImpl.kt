package build.wallet.cloud.store

import build.wallet.catching
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import platform.Foundation.NSFileManager

@Suppress("ClassName", "unused")
class iCloudAccountRepositoryImpl : iCloudAccountRepository {
  override fun currentAccount(): Result<iCloudAccount?, CloudStoreAccountError> {
    return Result
      .catching {
        NSFileManager.defaultManager.ubiquityIdentityToken
          ?.description
          ?.let { iCloudAccount(it) }
      }
      .mapError { iCloudAccountError(it) }
  }
}
