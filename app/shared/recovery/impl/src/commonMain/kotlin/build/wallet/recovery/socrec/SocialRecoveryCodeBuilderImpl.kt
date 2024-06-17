package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.PakeCode
import build.wallet.ensure
import build.wallet.serialization.Base32Encoding
import build.wallet.serialization.checksum.CRC6
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.mapError
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.Sign
import com.ionspin.kotlin.bignum.integer.toBigInteger
import okio.ByteString.Companion.toByteString
import build.wallet.recovery.socrec.InviteCodeParts.Schema as InviteSchema
import build.wallet.recovery.socrec.RecoveryCodeParts.Schema as RecoverySchema

class SocialRecoveryCodeBuilderImpl(
  val base32Encoding: Base32Encoding,
) : SocialRecoveryCodeBuilder {
  override fun buildInviteCode(
    serverPart: String,
    serverBits: Int,
    pakePart: PakeCode,
  ): Result<String, SocialRecoveryCodeBuilderError> =
    binding {
      ensure(pakePart.bytes.toByteArray().size >= InviteSchema.pakeByteArraySize()) {
        SocialRecoveryCodeEncodingError("Pake data is too small.")
      }
      ensure(serverPart.length >= serverBits / 4) {
        SocialRecoveryCodeEncodingError("Server data is too small.")
      }

      val version = InviteSchema.VERSION
      val dataLength = InviteSchema.VERSION_BITS + InviteSchema.PAKE_BITS + serverBits
      val totalLength = dataLength + InviteSchema.CRC_BITS
      val padding = paddingForByteAlignment(totalLength)
      val insignificantPakeBits = paddingForByteAlignment(InviteSchema.PAKE_BITS)
      val insignificantServerBits = paddingForByteAlignment(serverBits)

      val password = BigInteger.fromByteArray(pakePart.bytes.toByteArray(), Sign.POSITIVE)
        .shr(insignificantPakeBits)

      val serverCode = serverPart.toBigInteger(16)
        .shr(insignificantServerBits)

      val crc = calculateCrc(
        version = version,
        pakeData = password,
        pakeLength = InviteSchema.PAKE_BITS,
        serverData = serverCode,
        serverLength = serverBits
      )

      version.toBigInteger()
        .shl(InviteSchema.PAKE_BITS)
        .unsignedOr(password)
        .shl(serverBits)
        .unsignedOr(serverCode)
        .shl(InviteSchema.CRC_BITS)
        .unsignedOr(crc.toBigInteger())
        .shl(padding)
        .let {
          val bufSize = (totalLength + padding).bitLengthToByteArraySize()
          base32Encoding
            .encode(it.toFixedSizeByteArray(bufSize).toByteString())
            .mapError { error ->
              SocialRecoveryCodeEncodingError(
                message = "Error encoding invite code.",
                cause = error
              )
            }
            .bind()
            .take(totalLength / Base32Encoding.BITS_PER_CHAR)
        }
        .chunked(4)
        .joinToString("-")
    }

  private fun Int.bitLengthToByteArraySize(): Int {
    return (this + (Byte.SIZE_BITS - 1)) / Byte.SIZE_BITS
  }

  private fun paddingForByteAlignment(bits: Int): Int {
    val mod = bits % Byte.SIZE_BITS
    if (mod == 0) {
      return 0
    }
    return Byte.SIZE_BITS - mod
  }

  override fun buildRecoveryCode(
    serverPart: Int,
    pakePart: PakeCode,
  ): Result<String, Throwable> {
    if (pakePart.bytes.toByteArray().size < RecoverySchema.pakeByteArraySize()) {
      return Err(SocialRecoveryCodeEncodingError("Pake data is too small."))
    }
    val insignificantPakeBits = paddingForByteAlignment(RecoverySchema.PAKE_BITS)

    val password = BigInteger.fromByteArray(pakePart.bytes.toByteArray(), Sign.POSITIVE)
      .shr(insignificantPakeBits)

    val serverCode = serverPart.toBigInteger()
    val serverLength = serverCode.bitLength()

    val crc = calculateCrc(
      version = RecoverySchema.VERSION,
      pakeData = password,
      pakeLength = RecoverySchema.PAKE_BITS,
      serverData = serverCode,
      serverLength = serverLength
    )

    val preamble = 1.toBigInteger()

    return preamble
      .shl(RecoverySchema.VERSION_BITS)
      .unsignedOr(RecoverySchema.VERSION.toBigInteger())
      .shl(RecoverySchema.PAKE_BITS)
      .unsignedOr(password)
      .shl(serverLength)
      .unsignedOr(serverCode)
      .shl(RecoverySchema.CRC_BITS)
      .unsignedOr(crc.toBigInteger())
      .toString()
      .let(::Ok)
  }

  override fun parseInviteCode(
    inviteCode: String,
  ): Result<InviteCodeParts, SocialRecoveryCodeBuilderError> =
    binding {
      val inviteCode = inviteCode.toSanitizedCode()
      val expectedMinBitLength =
        (InviteSchema.VERSION_BITS + InviteSchema.PAKE_BITS + InviteSchema.MIN_SERVER_BITS + InviteSchema.CRC_BITS)
      val expectedMinCharacters = expectedMinBitLength / Base32Encoding.BITS_PER_CHAR

      ensure(inviteCode.length >= expectedMinCharacters) {
        SocialRecoveryCodeEncodingError(
          "Invalid code length. Got ${inviteCode.length}, but expected at least $expectedMinCharacters."
        )
      }
      val serverBits =
        inviteCode.length * 5 - InviteSchema.VERSION_BITS - InviteSchema.PAKE_BITS - InviteSchema.CRC_BITS

      val data = base32Encoding
        .decode(inviteCode.alignBase32Code())
        .mapError { error ->
          SocialRecoveryCodeEncodingError(message = "Error decoding invite code.", cause = error)
        }
        .bind()
        .toByteArray()
        .let { BigInteger.fromByteArray(it, Sign.POSITIVE) }
      val significantBits = inviteCode.length * Base32Encoding.BITS_PER_CHAR
      val padding = paddingForByteAlignment(significantBits)
      val insignificantPakeBits = paddingForByteAlignment(InviteSchema.PAKE_BITS)
      val insignificantServerBits = paddingForByteAlignment(serverBits)
      val totalLength = significantBits + padding
      val version = data.shr(totalLength - InviteSchema.VERSION_BITS).intValue()
      ensure(version == InviteSchema.VERSION) {
        SocialRecoveryCodeVersionError("Invalid version")
      }

      val crc = data.shr(padding).and(createMask(InviteSchema.CRC_BITS)).byteValue()
      val serverPart = data.shr(padding + InviteSchema.CRC_BITS).and(createMask(serverBits))
      val pakePart =
        data.shr(padding + InviteSchema.CRC_BITS + serverBits).and(createMask(InviteSchema.PAKE_BITS))
      val calculatedCrc = calculateCrc(
        version = version,
        pakeData = pakePart,
        pakeLength = InviteSchema.PAKE_BITS,
        serverData = serverPart,
        serverLength = serverBits
      )

      // Verify that the CRC in the code matched the decoded data's CRC.
      ensure(crc == calculatedCrc) {
        SocialRecoveryCodeEncodingError("Invalid code. Checksum did not match.")
      }

      InviteCodeParts(
        serverPart = serverPart.shl(insignificantServerBits)
          .toFixedSizeByteArray(serverBits.bitLengthToByteArraySize()).toByteString().hex(),
        pakePart = PakeCode(
          pakePart.shl(insignificantPakeBits)
            .toFixedSizeByteArray(InviteSchema.PAKE_BITS.bitLengthToByteArraySize()).toByteString()
        )
      )
    }

  /**
   * Add padding to the end of a base32 string to ensure that the data is byte-aligned.
   */
  private fun String.alignBase32Code(): String {
    val size = (this.length * 5).bitLengthToByteArraySize() * Byte.SIZE_BITS
    val pad = size - (this.length * 5)
    return if (pad == 0) {
      this
    } else if (pad <= 5) {
      this + "0"
    } else {
      this + "00"
    }
  }

  override fun parseRecoveryCode(
    recoveryCode: String,
  ): Result<RecoveryCodeParts, SocialRecoveryCodeBuilderError> {
    val recoveryCode = recoveryCode.toSanitizedCode()
    lateinit var fullData: BigInteger
    try {
      fullData = recoveryCode.toBigInteger()
    } catch (e: NumberFormatException) {
      return Err(SocialRecoveryCodeEncodingError("Invalid code, can't parse to BigInteger"))
    }
    val significantBits = (fullData.bitLength() - 1)
    val data = fullData.removePreambleBit()
    val fixedDataLength =
      RecoverySchema.VERSION_BITS + RecoverySchema.PAKE_BITS + RecoverySchema.CRC_BITS

    if (significantBits < fixedDataLength) {
      return Err(
        SocialRecoveryCodeEncodingError(
          "Invalid code length. Got $significantBits, but expected at least $fixedDataLength."
        )
      )
    }

    val serverPartLength = significantBits - fixedDataLength
    val insignificantPakeBits = paddingForByteAlignment(RecoverySchema.PAKE_BITS)
    val version = data.shr(significantBits - RecoverySchema.VERSION_BITS).intValue()

    if (version != RecoverySchema.VERSION) return Err(SocialRecoveryCodeVersionError("Invalid version"))

    val crc = data.and(createMask(RecoverySchema.CRC_BITS)).byteValue()
    val serverPart = data.shr(RecoverySchema.CRC_BITS).and(createMask(serverPartLength))
    val pakePart =
      data.shr(serverPartLength + RecoverySchema.CRC_BITS).and(createMask(RecoverySchema.PAKE_BITS))
    val calculatedCrc = calculateCrc(
      version = version,
      pakeData = pakePart,
      pakeLength = RecoverySchema.PAKE_BITS,
      serverData = serverPart,
      serverLength = serverPartLength
    )

    // Verify that the CRC in the code matched the decoded data's CRC.
    if (crc != calculatedCrc) {
      return Err(SocialRecoveryCodeEncodingError("Invalid code. Checksum did not match."))
    }

    return RecoveryCodeParts(
      serverPart = serverPart.intValue(true),
      pakePart = PakeCode(
        pakePart.shl(insignificantPakeBits)
          .toFixedSizeByteArray(RecoverySchema.PAKE_BITS.bitLengthToByteArraySize()).toByteString()
      )
    ).let(::Ok)
  }

  /**
   * Creates a mask only leaving the last [length] bits set to 1.
   */
  private fun createMask(length: Int): BigInteger = (1.toBigInteger() shl length) - 1

  /**
   * Sets the leading bit in the BigInteger to 0.
   *
   * The leading bit, or preamble, is used as a marker to calculate
   * the size of the data stream represented by this number.
   * This preamble is required when the data chunks are variable in size,
   * but is not needed for parsing and can be removed by this method.
   */
  private fun BigInteger.removePreambleBit(): BigInteger {
    return setBitAt(bitLength().minus(1).toLong(), false)
  }

  private fun calculateCrc(
    version: Int,
    pakeData: BigInteger,
    pakeLength: Int,
    serverData: BigInteger,
    serverLength: Int,
  ): Byte {
    val dataBytes = version.toBigInteger()
      .shl(pakeLength)
      .unsignedOr(pakeData)
      .shl(serverLength)
      .unsignedOr(serverData)
      .toByteArray()

    return CRC6.calculate(dataBytes)
  }

  /**
   * Bitwise or operation that handles zero values correctly for BigIntegers.
   */
  private fun BigInteger.unsignedOr(other: BigInteger): BigInteger {
    return if (this == BigInteger.ZERO) other else or(other)
  }
}

private fun String.toSanitizedCode(): String =
  this
    .replace("-", "")
    .trim()
