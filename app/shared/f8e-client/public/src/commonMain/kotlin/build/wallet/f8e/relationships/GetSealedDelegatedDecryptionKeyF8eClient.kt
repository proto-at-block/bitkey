package build.wallet.f8e.relationships

import build.wallet.bitkey.f8e.AccountId
import build.wallet.crypto.SealedData
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

fun interface GetSealedDelegatedDecryptionKeyF8eClient {
  suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<SealedData, NetworkingError>
}
