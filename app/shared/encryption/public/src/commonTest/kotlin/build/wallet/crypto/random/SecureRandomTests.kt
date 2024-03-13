package build.wallet.crypto.random

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import okio.ByteString
import okio.ByteString.Companion.toByteString
import kotlin.experimental.and
import kotlin.experimental.or

class SecureRandomTests : FunSpec({
  test("generates random numbers") {
    val random = SecureRandom()
    val size = 8

    // Don't really know how to check this is working properly. We'll generate
    // 100 random 8 byte random numbers and ensure that each bit is set or unset at least
    // once at some point. As in, the logical AND of all numbers results in 0, and
    // the logical OR of all numbers results in 2^64.
    // And we check that all 100 numbers are different, with a ~2^-9 chance of
    // hitting the birthday problem.
    var and = ByteArray(8) { 0xFF.toByte() }
    var or = ByteArray(8) { 0x00.toByte() }
    val seen = mutableSetOf<ByteString>()

    for (i in 1..100) {
      val bytes = ByteArray(size)
      random.nextBytes(bytes)
      bytes.size shouldBe size
      and = and.mapIndexed { index, byte -> byte.and(bytes[index]) }.toByteArray()
      or = or.mapIndexed { index, byte -> byte.or(bytes[index]) }.toByteArray()
      seen += bytes.toByteString()
    }

    and shouldBe ByteArray(size) { 0x00.toByte() }
    or shouldBe ByteArray(size) { 0xFF.toByte() }
    seen.size shouldBe 100
  }

  test("generates random numbers of a specific size") {
    val random = SecureRandom()
    val bytes = random.nextBytes(8)
    bytes.size shouldBe 8
  }
})
