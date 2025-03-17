package build.wallet.relationships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.AccountId
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.crypto.SealedData
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.ktor.util.*
import okio.ByteString
import okio.ByteString.Companion.decodeBase64

class DelegatedDecryptionKeyServiceMock(
  var uploadResult: Result<Unit, Error> = Ok(Unit),
  val uploadCalls: Turbine<Unit>? = null,
) : DelegatedDecryptionKeyService {
  override suspend fun uploadSealedDelegatedDecryptionKeyData(
    fullAccountId: FullAccountId,
    sealedData: SealedData,
  ): Result<Unit, Error> {
    uploadCalls?.add(Unit)
    return uploadResult
  }

  override suspend fun getSealedDelegatedDecryptionKeyData(
    accountId: AccountId,
  ): Result<SealedData, Error> {
    return Ok("sealed-data".encodeBase64().decodeBase64()!!)
  }

  override suspend fun restoreDelegatedDecryptionKey(
    unsealedData: ByteString,
  ): Result<Unit, RelationshipsKeyError> {
    return Ok(Unit)
  }
}
