package build.wallet.f8e.relationships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.SealedData
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import com.github.michaelbull.result.Result

fun interface UploadSealedDelegatedDecryptionKeyF8eClient {
  suspend fun uploadSealedDelegatedDecryptionKeyData(
    accountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    sealedData: SealedData,
  ): Result<Unit, NetworkingError>
}
