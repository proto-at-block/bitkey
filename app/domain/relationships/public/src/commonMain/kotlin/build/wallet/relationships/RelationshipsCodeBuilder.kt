package build.wallet.relationships

import build.wallet.bitkey.relationships.PakeCode
import build.wallet.recovery.socrec.toFixedSizeByteArray
import com.github.michaelbull.result.Result
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.ionspin.kotlin.bignum.integer.toBigInteger
import okio.ByteString.Companion.toByteString
import kotlin.math.ceil

sealed class RelationshipsCodeBuilderError(
  override val message: String = "Social Recovery Code Encoding Error",
  override val cause: Throwable? = null,
) : Error()

data class RelationshipsCodeEncodingError(
  override val message: String = "Error parsing the social recovery code",
  override val cause: Throwable? = null,
) : RelationshipsCodeBuilderError()

data class RelationshipsCodeVersionError(
  override val message: String = "Version mismatch between the code and the schema",
  override val cause: Throwable? = null,
) : RelationshipsCodeBuilderError()

/**
 * A builder for encoding and decoding social recovery invite and challenge codes.
 */
interface RelationshipsCodeBuilder {
  fun buildInviteCode(
    serverPart: String,
    serverBits: Int,
    pakePart: PakeCode,
  ): Result<String, RelationshipsCodeBuilderError>

  fun parseInviteCode(inviteCode: String): Result<InviteCodeParts, RelationshipsCodeBuilderError>

  fun buildRecoveryCode(
    serverPart: Int,
    pakePart: PakeCode,
  ): Result<String, Throwable>

  fun parseRecoveryCode(
    recoveryCode: String,
  ): Result<RecoveryCodeParts, RelationshipsCodeBuilderError>
}

data class InviteCodeParts(
  val serverPart: String,
  val pakePart: PakeCode,
) {
  object Schema {
    const val VERSION = 0
    const val VERSION_BITS = 1
    const val PAKE_BITS = 23
    const val MIN_SERVER_BITS = 20
    const val CRC_BITS = 6

    fun pakeByteArraySize() = ceil(PAKE_BITS.toDouble() / Byte.SIZE_BITS).toInt()

    /**
     * Mask random data to fit the format required for an invite code's Pake data.
     */
    fun maskPakeData(random: ByteArray): PakeCode {
      val randomBitSize = random.size * Byte.SIZE_BITS
      require(randomBitSize >= PAKE_BITS) {
        "Random data is smaller than required length."
      }
      val insignificantDigits = randomBitSize - PAKE_BITS
      val mask = (1.toBigInteger() shl PAKE_BITS) - 1
      val working = BigInteger.fromByteArray(random, Sign.POSITIVE)
      val maskedData = working.and(mask).shl(insignificantDigits)

      return PakeCode(maskedData.toFixedSizeByteArray(pakeByteArraySize()).toByteString())
    }
  }
}

data class RecoveryCodeParts(
  val serverPart: Int,
  val pakePart: PakeCode,
) {
  object Schema {
    const val VERSION = 0
    const val VERSION_BITS = 1
    const val PAKE_BITS = 35
    const val CRC_BITS = 6

    fun pakeByteArraySize() = ceil(PAKE_BITS.toDouble() / Byte.SIZE_BITS).toInt()

    /**
     * Mask random data to fit the format required for an recovery code's Pake data.
     */
    fun maskPakeData(random: ByteArray): PakeCode {
      val randomBitSize = random.size * Byte.SIZE_BITS
      require(randomBitSize >= PAKE_BITS) {
        "Random data is smaller than required length."
      }
      val insignificantDigits = randomBitSize - PAKE_BITS
      val mask = (1.toBigInteger() shl PAKE_BITS) - 1
      val working = BigInteger.fromByteArray(random, Sign.POSITIVE)
      val maskedData = working.and(mask).shl(insignificantDigits)

      return PakeCode(maskedData.toFixedSizeByteArray(pakeByteArraySize()).toByteString())
    }
  }
}
