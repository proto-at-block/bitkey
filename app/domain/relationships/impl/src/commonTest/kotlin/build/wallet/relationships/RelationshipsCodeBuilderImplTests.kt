package build.wallet.relationships

import build.wallet.bitkey.relationships.PakeCode
import build.wallet.testing.shouldBeErrOfType
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.toByteString

class RelationshipsCodeBuilderImplTests : FunSpec({

  val builder = RelationshipsCodeBuilderImpl()

  test("test buildInviteCode") {
    val serverPart = "abc123"
    val pakePart = ubyteArrayOf(0xfeu, 0xdcu, 0x20u).toByteArray()
    val inviteCode = builder.buildInviteCode(
      serverPart,
      20,
      PakeCode(ByteString.of(*pakePart))
    )
    inviteCode.shouldBe(Ok("FXQ1-1AY1-4W"))
  }

  test("test buildInviteCode - pake data too short") {
    val serverPart = "abc123"
    val pakePart = ubyteArrayOf(0x1u).toByteArray()
    val inviteCode = builder.buildInviteCode(
      serverPart,
      20,
      PakeCode(ByteString.of(*pakePart))
    )
    inviteCode.shouldBe(Err(RelationshipsCodeEncodingError("Pake data is too small.")))
  }

  test("test buildInviteCode - server data too short") {
    val serverPart = "a"
    val pakePart = ubyteArrayOf(0xfeu, 0xdcu, 0x20u).toByteArray()
    val inviteCode = builder.buildInviteCode(
      serverPart,
      20,
      PakeCode(ByteString.of(*pakePart))
    )
    inviteCode.shouldBe(Err(RelationshipsCodeEncodingError("Server data is too small.")))
  }

  test("test buildRecoveryCode") {
    val serverPart = 703506
    val pakePart = ubyteArrayOf(0xfeu, 0xdcu, 0x20u, 0x00u, 0x40u).toByteArray()

    val code = builder.buildRecoveryCode(
      serverPart,
      PakeCode(ByteString.of(*pakePart))
    )

    code.shouldBe(Ok(0b1_0_11111110110111000010000000000000010_10101011110000010010_001010.toString(10)))
  }

  test("test buildRecoveryCode - pake data too short") {
    val serverPart = 703506
    val pakePart = ubyteArrayOf(0x1u).toByteArray()

    val code = builder.buildRecoveryCode(
      serverPart,
      PakeCode(ByteString.of(*pakePart))
    )

    code.shouldBe(Err(RelationshipsCodeEncodingError("Pake data is too small.")))
  }

  test("test parseInviteCode - success") {
    val inviteCode = "FXQ11AY14W"
    val serverPart = "abc120"
    val pakePart = ubyteArrayOf(0xfeu, 0xdcu, 0x20u).toByteArray().toByteString()
    val pakeCode = PakeCode(pakePart)
    builder.parseInviteCode(inviteCode).getOrThrow().should {
      it.pakePart.shouldBe(pakeCode)
      it.serverPart.shouldBe(serverPart)
    }
  }

  test("test parseInviteCode - invalid length (too short)") {
    val inviteCode = "FXQ11AY19"
    builder.parseInviteCode(inviteCode).shouldBeErrOfType<RelationshipsCodeBuilderError>().message.shouldBe(
      "Invalid code length. Got 9, but expected at least 10."
    )
  }

  test("test parseInviteCode - checksum failure") {
    val inviteCode = "FXQ11AY14Z"
    builder.parseInviteCode(inviteCode).shouldBeErrOfType<RelationshipsCodeBuilderError>().message.shouldBe(
      "Invalid code. Checksum did not match."
    )
  }

  // todo(W-8553): fix version mismatch validation, parser does not throw a RelationshipsCodeVersionError
  test("test parseInviteCode - version mismatch") {
    val inviteCode = "FXQ11AY14WS"
    builder.parseInviteCode(inviteCode).shouldBeErrOfType<RelationshipsCodeBuilderError>()
  }

  test("test parseRecoveryCode") {
    val recoveryCode = 0b1_0_11111110110111000010000000000000010_10101011110000010010_001010u.toString()

    val result = builder.parseRecoveryCode(recoveryCode).getOrThrow()
    result.serverPart.shouldBe(703506)
    result.pakePart.bytes.shouldBe(ubyteArrayOf(0xfeu, 0xdcu, 0x20u, 0x00u, 0x40u).toByteArray().toByteString())
  }

  test("test parseRecoveryCode - version mismatch") {
    val recoveryCode = 0b1_1_11111110110111000010000000000000010_10101011110000010010_001010u.toString()
    val result = builder.parseRecoveryCode(recoveryCode).shouldBeErrOfType<RelationshipsCodeVersionError>()
  }

  test("test parseRecoveryCode - invalid code format") {
    val recoveryCode = "1.0.91"

    builder.parseRecoveryCode(recoveryCode)
      .shouldBeErrOfType<RelationshipsCodeBuilderError>()
      .message.shouldBe("Invalid code, can't parse to BigInteger")
  }
})
