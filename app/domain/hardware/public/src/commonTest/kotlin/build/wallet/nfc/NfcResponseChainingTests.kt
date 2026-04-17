package build.wallet.nfc

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

/**
 * An [NfcSession] that returns a scripted sequence of responses from [transceive].
 * Each call pops the next response and records the buffer that was sent.
 */
private class ScriptedNfcSession(
  responses: List<List<UByte>>,
) : NfcSession {
  override val parameters = NfcSessionFake.FakeParameters
  override var message: String? = null
  override fun close() = Unit

  private val pendingResponses = responses.toMutableList()
  val sentBuffers = mutableListOf<List<UByte>>()

  override suspend fun transceive(buffer: List<UByte>): List<UByte> {
    sentBuffers.add(buffer)
    return pendingResponses.removeAt(0)
  }
}

class NfcResponseChainingTests : FunSpec({

  test("no chaining - response returned unchanged when SW1 is not 0x61") {
    // Response: [0xAA, 0xBB, SW1=0x90, SW2=0x00] — success, no chaining
    val response = listOf<UByte>(0xAAu, 0xBBu, 0x90u, 0x00u)
    val session = ScriptedNfcSession(listOf(response))

    val result = session.transceiveWithChaining(listOf(0x01u))

    result shouldBe response
    session.sentBuffers.size shouldBe 1
  }

  test("no chaining - short response with fewer than 2 bytes returned unchanged") {
    val response = listOf<UByte>(0x90u)
    val session = ScriptedNfcSession(listOf(response))

    val result = session.transceiveWithChaining(listOf(0x01u))

    result shouldBe response
    session.sentBuffers.size shouldBe 1
  }

  test("no chaining - empty response returned unchanged") {
    val response = emptyList<UByte>()
    val session = ScriptedNfcSession(listOf(response))

    val result = session.transceiveWithChaining(listOf(0x01u))

    result.shouldBeEmpty()
    session.sentBuffers.size shouldBe 1
  }

  test("single GET RESPONSE - two chunks reassembled into one response") {
    // Chunk 1: [0xAA, 0xBB, SW1=0x61, SW2=0x02] — more data
    // Chunk 2: [0xCC, 0xDD, SW1=0x90, SW2=0x00] — final
    // Expected: [0xAA, 0xBB, 0xCC, 0xDD, 0x90, 0x00]
    val chunk1 = listOf<UByte>(0xAAu, 0xBBu, 0x61u, 0x02u)
    val chunk2 = listOf<UByte>(0xCCu, 0xDDu, 0x90u, 0x00u)
    val session = ScriptedNfcSession(listOf(chunk1, chunk2))

    val result = session.transceiveWithChaining(listOf(0x01u))

    result shouldBe listOf<UByte>(0xAAu, 0xBBu, 0xCCu, 0xDDu, 0x90u, 0x00u)
    session.sentBuffers.size shouldBe 2
  }

  test("multiple GET RESPONSEs - three chunks reassembled into one response") {
    // Chunk 1: [0xAA, SW1=0x61, SW2=0x04]
    // Chunk 2: [0xBB, SW1=0x61, SW2=0x02]
    // Chunk 3: [0xCC, SW1=0x90, SW2=0x00]
    // Expected: [0xAA, 0xBB, 0xCC, 0x90, 0x00]
    val chunk1 = listOf<UByte>(0xAAu, 0x61u, 0x04u)
    val chunk2 = listOf<UByte>(0xBBu, 0x61u, 0x02u)
    val chunk3 = listOf<UByte>(0xCCu, 0x90u, 0x00u)
    val session = ScriptedNfcSession(listOf(chunk1, chunk2, chunk3))

    val result = session.transceiveWithChaining(listOf(0x01u))

    result shouldBe listOf<UByte>(0xAAu, 0xBBu, 0xCCu, 0x90u, 0x00u)
    session.sentBuffers.size shouldBe 3
  }

  test("GET RESPONSE sends correct WCA APDU bytes") {
    val chunk1 = listOf<UByte>(0xAAu, 0x61u, 0x01u)
    val chunk2 = listOf<UByte>(0xBBu, 0x90u, 0x00u)
    val session = ScriptedNfcSession(listOf(chunk1, chunk2))

    session.transceiveWithChaining(listOf(0x01u, 0x02u))

    // First call: the original APDU
    session.sentBuffers[0] shouldBe listOf<UByte>(0x01u, 0x02u)
    // Second call: WCA GET RESPONSE (CLA=0x87, INS=0x78, P1=0x00, P2=0x00)
    session.sentBuffers[1] shouldBe listOf<UByte>(0x87u, 0x78u, 0x00u, 0x00u)
  }

  test("chaining with empty payload chunks accumulates correctly") {
    // Chunk 1: [SW1=0x61, SW2=0x02] — no payload, just status
    // Chunk 2: [0xAA, 0xBB, SW1=0x90, SW2=0x00] — final with payload
    // Expected: [0xAA, 0xBB, 0x90, 0x00]
    val chunk1 = listOf<UByte>(0x61u, 0x02u)
    val chunk2 = listOf<UByte>(0xAAu, 0xBBu, 0x90u, 0x00u)
    val session = ScriptedNfcSession(listOf(chunk1, chunk2))

    val result = session.transceiveWithChaining(listOf(0x01u))

    result shouldBe listOf<UByte>(0xAAu, 0xBBu, 0x90u, 0x00u)
  }

  test("throws on malformed chained response with fewer than 2 bytes") {
    // First response triggers chaining, second response is malformed (1 byte, no SW1/SW2)
    val chunk1 = listOf<UByte>(0xAAu, 0x61u, 0x01u)
    val malformed = listOf<UByte>(0xFFu)
    val session = ScriptedNfcSession(listOf(chunk1, malformed))

    val exception = shouldThrow<NfcException.CommandError> {
      session.transceiveWithChaining(listOf(0x01u))
    }
    exception.cause?.message.shouldContain("Malformed chained APDU response")
  }

  test("throws when chaining exceeds maximum iterations") {
    // 22 responses: first triggers chaining, then 21 more all return SW1=0x61
    // exceeds the 20-iteration cap
    val responses = buildList {
      repeat(22) { add(listOf<UByte>(0xAAu, 0x61u, 0x01u)) }
    }
    val session = ScriptedNfcSession(responses)

    val exception = shouldThrow<NfcException.CommandError> {
      session.transceiveWithChaining(listOf(0x01u))
    }
    exception.cause?.message.shouldContain("exceeded")
  }

  test("chaining where final chunk is status-only") {
    // Chunk 1: [0xAA, 0xBB, SW1=0x61, SW2=0x00]
    // Chunk 2: [SW1=0x90, SW2=0x00] — final, no additional payload
    // Expected: [0xAA, 0xBB, 0x90, 0x00]
    val chunk1 = listOf<UByte>(0xAAu, 0xBBu, 0x61u, 0x00u)
    val chunk2 = listOf<UByte>(0x90u, 0x00u)
    val session = ScriptedNfcSession(listOf(chunk1, chunk2))

    val result = session.transceiveWithChaining(listOf(0x01u))

    result shouldBe listOf<UByte>(0xAAu, 0xBBu, 0x90u, 0x00u)
  }
})
