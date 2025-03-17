package build.wallet.relationships

import build.wallet.bitkey.relationships.PakeCode
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import okio.ByteString.Companion.toByteString

class RelationshipsCodeBuilderFakeTests : FunSpec({
  val builder = RelationshipsCodeBuilderFake()
  test("inviteCode round trip") {
    val serverPart = "AC32ZQ52"
    val pakePart = PakeCode(byteArrayOf(0x12, 0x34).toByteString())
    val inviteCode = builder.buildInviteCode(serverPart, 40, pakePart).getOrThrow()
    inviteCode shouldBe "1234,AC32ZQ52"
    val parts = builder.parseInviteCode(inviteCode).getOrThrow()
    parts.serverPart shouldBeEqual serverPart
    parts.pakePart shouldBeEqual pakePart
  }

  test("challenge code round trip") {
    val serverPart = 123456
    val pakePart = PakeCode(byteArrayOf(0x12, 0x34).toByteString())
    val inviteCode = builder.buildRecoveryCode(serverPart, pakePart).getOrThrow()
    inviteCode shouldBe "1234,123456"
    val parts = builder.parseRecoveryCode(inviteCode).getOrThrow()
    parts.serverPart shouldBeEqual serverPart
    parts.pakePart shouldBeEqual pakePart
  }
})
