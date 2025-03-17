package build.wallet.cloud.store

import com.github.michaelbull.result.Result

@Suppress("ClassName")
interface iCloudAccountRepository {
  fun currentAccount(): Result<iCloudAccount?, CloudStoreAccountError>

  suspend fun currentUbiquityContainerPath(): Result<String?, CloudStoreAccountError>
}
