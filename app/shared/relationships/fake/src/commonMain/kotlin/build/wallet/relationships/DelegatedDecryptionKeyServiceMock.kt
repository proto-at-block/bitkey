package build.wallet.relationships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.SealedData
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.util.*
import io.ktor.utils.io.core.*
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

class DelegatedDecryptionKeyServiceMock(
  var uploadResult: Result<Unit, Error> = Ok(Unit),
  val uploadCalls: Turbine<Unit>? = null,
) : DelegatedDecryptionKeyService {
  override suspend fun uploadSealedDelegatedDecryptionKeyData(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    sealedData: SealedData,
  ): Result<Unit, Error> {
    uploadCalls?.add(Unit)
    return uploadResult
  }

  override suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId?,
    f8eEnvironment: F8eEnvironment?,
  ): Result<SealedData, Error> {
    return Ok("sealed-data".encodeBase64().decodeBase64()!!)
  }

  override suspend fun restoreDelegatedDecryptionKey(
    unsealedData: ByteString,
  ): Result<Unit, RelationshipsKeyError> {
    return Ok(Unit)
  }
}
