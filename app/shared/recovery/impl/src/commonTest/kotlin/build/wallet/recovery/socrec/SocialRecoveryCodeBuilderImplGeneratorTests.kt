package build.wallet.recovery.socrec

import build.wallet.bitkey.socrec.PakeCode
import build.wallet.serialization.Base32Encoding
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.string.shouldHaveLength
import io.kotest.matchers.string.shouldHaveLengthBetween
import io.kotest.property.Arb
import io.kotest.property.arbitrary.ArbitraryBuilderContext
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.byteArray
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.experimental.and

@OptIn(ExperimentalStdlibApi::class)
class SocialRecoveryCodeBuilderImplGeneratorTests : FunSpec({
  // Generate a random left-justified byte array of the given bit length
  suspend fun ArbitraryBuilderContext.getBitsLeftJustified(numBits: Int): ByteArray {
    val numBytes = (numBits + (Byte.SIZE_BITS - 1)) / Byte.SIZE_BITS
    val bytes = Arb.byteArray(Arb.constant(numBytes), Arb.byte(Byte.MIN_VALUE, Byte.MAX_VALUE)).bind()
    val mask = (0xFF shl (numBytes * 8 - numBits)).toByte()
    bytes[bytes.size - 1] = bytes.last() and mask
    return bytes
  }

  val codeBuilder = SocialRecoveryCodeBuilderImpl(Base32Encoding())

  context("invite code generator tests") {
    data class TestCase(
      val serverPart: String,
      val serverBitLength: Int,
      val pakePart: ByteString,
    )

    val arbWithFixedServerBitLength = arbitrary {
      val serverBitLength = 20
      val serverPart = getBitsLeftJustified(serverBitLength)
      val pakeBitLength = 23
      val pakePart = getBitsLeftJustified(pakeBitLength)

      TestCase(
        serverPart = serverPart.toHexString(),
        serverBitLength = serverBitLength,
        pakePart = pakePart.toByteString()
      )
    }

    checkAll(arbWithFixedServerBitLength) {
      val inviteCode = codeBuilder.buildInviteCode(it.serverPart, 20, PakeCode(it.pakePart)).getOrThrow()
      val parsed = codeBuilder.parseInviteCode(inviteCode).getOrThrow()

      inviteCode.shouldHaveLength(12)
      parsed.serverPart.shouldBeEqual(it.serverPart)
      parsed.pakePart.bytes.shouldBeEqual(it.pakePart)
    }

    val arbWithVariableServerBitLength = arbitrary {
      val serverBitLength = Arb.int(4, 10).bind() * 5
      val serverPart = getBitsLeftJustified(serverBitLength)
      val pakeBitLength = 23
      val pakePart = getBitsLeftJustified(pakeBitLength)

      TestCase(
        serverPart = serverPart.toHexString(),
        serverBitLength = serverBitLength,
        pakePart = pakePart.toByteString()
      )
    }

    checkAll(arbWithVariableServerBitLength) {
      val inviteCode = codeBuilder.buildInviteCode(it.serverPart, it.serverBitLength, PakeCode(it.pakePart)).getOrThrow()
      val parsed = codeBuilder.parseInviteCode(inviteCode).getOrThrow()

      inviteCode.replace("-", "").shouldHaveLength(it.serverBitLength / 5 + 6)
      parsed.serverPart.shouldBeEqual(it.serverPart)
      parsed.pakePart.bytes.shouldBeEqual(it.pakePart)
    }
  }

  context("recovery code generator tests") {
    data class TestCase(
      val serverPart: Int,
      val pakePart: ByteString,
    )

    val testCaseArb = arbitrary {
      val serverPart = Arb.int(0, 256).bind()
      val pakeBitLength = 35
      val pakePart = getBitsLeftJustified(pakeBitLength)

      TestCase(
        serverPart = serverPart,
        pakePart = pakePart.toByteString()
      )
    }

    checkAll(testCaseArb) {
      val recoveryCode = codeBuilder.buildRecoveryCode(it.serverPart, PakeCode(it.pakePart)).getOrThrow()
      val parsed = codeBuilder.parseRecoveryCode(recoveryCode).getOrThrow()

      recoveryCode.shouldHaveLengthBetween(13, 16)
      parsed.serverPart.shouldBeEqual(it.serverPart)
      parsed.pakePart.bytes.shouldBeEqual(it.pakePart)
    }
  }
})
