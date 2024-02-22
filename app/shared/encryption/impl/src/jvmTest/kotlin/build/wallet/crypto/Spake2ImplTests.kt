package build.wallet.crypto

import build.wallet.core.Spake2Exception
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okio.ByteString.Companion.toByteString

class Spake2ImplTests : FunSpec({

  fun performKeyExchange(
    alicePassword: ByteArray,
    bobPassword: ByteArray,
  ): Pair<Spake2Keys, Spake2Keys> {
    val alice = Spake2Impl(Spake2Role.Alice, "Alice", "Bob")
    val bob = Spake2Impl(Spake2Role.Bob, "Bob", "Alice")

    val aliceMsg = alice.generateMsg(alicePassword.toByteString())
    val bobMsg = bob.generateMsg(bobPassword.toByteString())

    val aliceKeys = alice.processMsg(bobMsg, null)
    val bobKeys = bob.processMsg(aliceMsg, null)

    return Pair(aliceKeys, bobKeys)
  }

  fun assertKeysMatch(
    aliceKeys: Spake2Keys,
    bobKeys: Spake2Keys,
    shouldMatch: Boolean,
  ) {
    aliceKeys.aliceEncryptionKey.toByteArray()
      .contentEquals(bobKeys.aliceEncryptionKey.toByteArray()) shouldBe shouldMatch
    aliceKeys.bobEncryptionKey.toByteArray()
      .contentEquals(bobKeys.bobEncryptionKey.toByteArray()) shouldBe shouldMatch
    aliceKeys.aliceConfKey.toByteArray()
      .contentEquals(bobKeys.aliceConfKey.toByteArray()) shouldBe shouldMatch
    aliceKeys.bobConfKey.toByteArray()
      .contentEquals(bobKeys.bobConfKey.toByteArray()) shouldBe shouldMatch
  }

  test("good round trip no confirmation") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "password".toByteArray(),
        "password".toByteArray()
      )
    assertKeysMatch(aliceKeys, bobKeys, true)
  }

  test("bob has wrong password no confirmation") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "password".toByteArray(),
        "passworf".toByteArray()
      )
    assertKeysMatch(aliceKeys, bobKeys, false)
  }

  test("alice has wrong password no confirmation") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "passworf".toByteArray(),
        "password".toByteArray()
      )
    assertKeysMatch(aliceKeys, bobKeys, false)
  }

  test("good round trip with confirmation") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "password".toByteArray(),
        "password".toByteArray()
      )
    assertKeysMatch(aliceKeys, bobKeys, true)

    val alice = Spake2Impl(Spake2Role.Alice, "Alice", "Bob")
    val bob = Spake2Impl(Spake2Role.Bob, "Bob", "Alice")

    val aliceKeyConfMsg = alice.generateKeyConfMsg(aliceKeys)
    val bobKeyConfMsg = bob.generateKeyConfMsg(bobKeys)

    alice.processKeyConfMsg(bobKeyConfMsg, aliceKeys)
    bob.processKeyConfMsg(aliceKeyConfMsg, bobKeys)
  }

  test("confirmation fails when password is wrong") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "password".toByteArray(),
        "passworf".toByteArray()
      )
    assertKeysMatch(aliceKeys, bobKeys, false)

    val alice = Spake2Impl(Spake2Role.Alice, "Alice", "Bob")
    val bob = Spake2Impl(Spake2Role.Bob, "Bob", "Alice")

    val aliceKeyConfMsg = alice.generateKeyConfMsg(aliceKeys)
    val bobKeyConfMsg = bob.generateKeyConfMsg(bobKeys)

    shouldThrow<Spake2Exception.MacException> {
      alice.processKeyConfMsg(bobKeyConfMsg, aliceKeys)
    }
    shouldThrow<Spake2Exception.MacException> {
      bob.processKeyConfMsg(aliceKeyConfMsg, bobKeys)
    }
  }

  test("same password results in different keys across sessions") {
    // First run
    val (aliceKeysFirst, bobKeysFirst) =
      performKeyExchange(
        "password".toByteArray(),
        "password".toByteArray()
      )
    assertKeysMatch(aliceKeysFirst, bobKeysFirst, true)

    // Second run
    val (aliceKeysSecond, bobKeysSecond) =
      performKeyExchange(
        "password".toByteArray(),
        "password".toByteArray()
      )
    assertKeysMatch(aliceKeysSecond, bobKeysSecond, true)

    // Ensure keys from the first and second runs are different
    with(aliceKeysFirst) {
      aliceEncryptionKey shouldNotBe aliceKeysSecond.aliceEncryptionKey
      bobEncryptionKey shouldNotBe aliceKeysSecond.bobEncryptionKey
      aliceConfKey shouldNotBe aliceKeysSecond.aliceConfKey
      bobConfKey shouldNotBe aliceKeysSecond.bobConfKey
    }

    with(bobKeysFirst) {
      aliceEncryptionKey shouldNotBe bobKeysSecond.aliceEncryptionKey
      bobEncryptionKey shouldNotBe bobKeysSecond.bobEncryptionKey
      aliceConfKey shouldNotBe bobKeysSecond.aliceConfKey
      bobConfKey shouldNotBe bobKeysSecond.bobConfKey
    }
  }
})
