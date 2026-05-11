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
  var getSealedDelegatedDecryptionKeyDataResult: Result<SealedData, Error> =
    Ok("sealed-data".encodeBase64().decodeBase64()!!)
  var getSealedDelegatedDecryptionKeyDataCalls = 0

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
    getSealedDelegatedDecryptionKeyDataCalls += 1
    return getSealedDelegatedDecryptionKeyDataResult
  }

  override suspend fun restoreDelegatedDecryptionKey(
    unsealedData: ByteString,
  ): Result<Unit, RelationshipsKeyError> {
    return Ok(Unit)
  }

  fun reset() {
    uploadResult = Ok(Unit)
    getSealedDelegatedDecryptionKeyDataResult = Ok("sealed-data".encodeBase64().decodeBase64()!!)
    getSealedDelegatedDecryptionKeyDataCalls = 0
  }
}
