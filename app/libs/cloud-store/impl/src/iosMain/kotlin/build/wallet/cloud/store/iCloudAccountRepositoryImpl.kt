package build.wallet.cloud.store

import build.wallet.catchingResult
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSFileManager

@Suppress("ClassName", "unused")
@BitkeyInject(AppScope::class)
class iCloudAccountRepositoryImpl : iCloudAccountRepository {
  override fun currentAccount(): Result<iCloudAccount?, CloudStoreAccountError> {
    return catchingResult {
      NSFileManager.defaultManager.ubiquityIdentityToken
        ?.description
        ?.let { iCloudAccount(it) }
    }
      .mapError { iCloudAccountError(it) }
  }

  override suspend fun currentUbiquityContainerPath(): Result<String?, CloudStoreAccountError> =
    catchingResult {
      withContext(Dispatchers.IO) {
        NSFileManager.defaultManager.URLForUbiquityContainerIdentifier(null)
          ?.description
      }
    }
      .mapError { iCloudAccountError(it) }
}
