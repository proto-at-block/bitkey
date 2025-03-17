package build.wallet.relationships

import build.wallet.bitkey.relationships.PakeCode
import build.wallet.catchingResult
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import okio.ByteString.Companion.toByteString

class RelationshipsCodeBuilderFake : RelationshipsCodeBuilder {
  override fun buildInviteCode(
    serverPart: String,
    serverBits: Int,
    pakePart: PakeCode,
  ): Result<String, RelationshipsCodeBuilderError> = Ok("${pakePart.bytes.hex()},$serverPart")

  override fun parseInviteCode(
    inviteCode: String,
  ): Result<InviteCodeParts, RelationshipsCodeBuilderError> =
    catchingResult {
      InviteCodeParts(
        serverPart = inviteCode.substringAfter(","),
        pakePart = PakeCode(inviteCode.substringBefore(",").hexToByteArray().toByteString())
      )
    }.mapError { RelationshipsCodeEncodingError(cause = it) }

  override fun buildRecoveryCode(
    serverPart: Int,
    pakePart: PakeCode,
  ): Result<String, Throwable> = Ok("${pakePart.bytes.hex()},$serverPart")

  override fun parseRecoveryCode(
    recoveryCode: String,
  ): Result<RecoveryCodeParts, RelationshipsCodeBuilderError> =
    catchingResult {
      RecoveryCodeParts(
        serverPart = recoveryCode.substringAfter(",").toInt(),
        pakePart = PakeCode(recoveryCode.substringBefore(",").hexToByteArray().toByteString())
      )
    }.mapError { RelationshipsCodeEncodingError(cause = it) }
}
