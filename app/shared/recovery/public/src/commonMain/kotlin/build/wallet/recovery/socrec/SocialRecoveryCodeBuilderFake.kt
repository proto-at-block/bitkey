package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.PakeCode
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import com.github.michaelbull.result.runCatching
import okio.ByteString.Companion.toByteString

@OptIn(ExperimentalStdlibApi::class)
class SocialRecoveryCodeBuilderFake : SocialRecoveryCodeBuilder {
  override fun buildInviteCode(
    serverPart: String,
    serverBits: Int,
    pakePart: PakeCode,
  ): Result<String, SocialRecoveryCodeBuilderError> = Ok("${pakePart.bytes.hex()},$serverPart")

  override fun parseInviteCode(
    inviteCode: String,
  ): Result<InviteCodeParts, SocialRecoveryCodeBuilderError> =
    runCatching {
      InviteCodeParts(
        serverPart = inviteCode.substringAfter(","),
        pakePart = PakeCode(inviteCode.substringBefore(",").hexToByteArray().toByteString())
      )
    }.mapError { SocialRecoveryCodeEncodingError(cause = it) }

  override fun buildRecoveryCode(
    serverPart: Int,
    pakePart: PakeCode,
  ): Result<String, Throwable> = Ok("${pakePart.bytes.hex()},$serverPart")

  override fun parseRecoveryCode(
    recoveryCode: String,
  ): Result<RecoveryCodeParts, SocialRecoveryCodeBuilderError> =
    runCatching {
      RecoveryCodeParts(
        serverPart = recoveryCode.substringAfter(",").toInt(),
        pakePart = PakeCode(recoveryCode.substringBefore(",").hexToByteArray().toByteString())
      )
    }.mapError { SocialRecoveryCodeEncodingError(cause = it) }
}
