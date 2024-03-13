package build.wallet.crypto

import build.wallet.core.Spake2Exception
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

class Spake2ImplTests : FunSpec({
  val spake2 = Spake2Impl()

  fun performKeyExchange(
    alicePassword: ByteString,
    bobPassword: ByteString,
  ): Pair<Spake2SymmetricKeys, Spake2SymmetricKeys> {
    val aliceParams = Spake2Params(Spake2Role.Alice, "Alice", "Bob", alicePassword)
    val bobParams = Spake2Params(Spake2Role.Bob, "Bob", "Alice", bobPassword)

    val aliceKeyPair = spake2.generateKeyPair(aliceParams)
    val bobKeyPair = spake2.generateKeyPair(bobParams)

    val aliceSymmetricKeys = spake2.processTheirPublicKey(aliceParams, aliceKeyPair, bobKeyPair.publicKey, null)
    val bobSymmetricKeys = spake2.processTheirPublicKey(bobParams, bobKeyPair, aliceKeyPair.publicKey, null)

    return Pair(aliceSymmetricKeys, bobSymmetricKeys)
  }

  fun assertKeysMatch(
    aliceKeys: Spake2SymmetricKeys,
    bobKeys: Spake2SymmetricKeys,
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
        "password".encodeUtf8(),
        "password".encodeUtf8()
      )
    assertKeysMatch(aliceKeys, bobKeys, true)
  }

  test("bob has wrong password no confirmation") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "password".encodeUtf8(),
        "passworf".encodeUtf8()
      )
    assertKeysMatch(aliceKeys, bobKeys, false)
  }
//
  test("alice has wrong password no confirmation") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "passworf".encodeUtf8(),
        "password".encodeUtf8()
      )
    assertKeysMatch(aliceKeys, bobKeys, false)
  }

  test("good round trip with confirmation") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "password".encodeUtf8(),
        "password".encodeUtf8()
      )
    assertKeysMatch(aliceKeys, bobKeys, true)

    val aliceKeyConfMsg = spake2.generateKeyConfMsg(Spake2Role.Alice, aliceKeys)
    val bobKeyConfMsg = spake2.generateKeyConfMsg(Spake2Role.Bob, bobKeys)

    spake2.processKeyConfMsg(Spake2Role.Alice, bobKeyConfMsg, aliceKeys)
    spake2.processKeyConfMsg(Spake2Role.Bob, aliceKeyConfMsg, bobKeys)
  }

  test("confirmation fails when password is wrong") {
    val (aliceKeys, bobKeys) =
      performKeyExchange(
        "password".encodeUtf8(),
        "passworf".encodeUtf8()
      )
    assertKeysMatch(aliceKeys, bobKeys, false)

    val aliceKeyConfMsg = spake2.generateKeyConfMsg(Spake2Role.Alice, aliceKeys)
    val bobKeyConfMsg = spake2.generateKeyConfMsg(Spake2Role.Bob, bobKeys)

    shouldThrow<Spake2Exception.MacException> {
      spake2.processKeyConfMsg(Spake2Role.Alice, bobKeyConfMsg, aliceKeys)
    }
    shouldThrow<Spake2Exception.MacException> {
      spake2.processKeyConfMsg(Spake2Role.Bob, aliceKeyConfMsg, bobKeys)
    }
  }
//
  test("same password results in different keys across sessions") {
    // First run
    val (aliceKeysFirst, bobKeysFirst) =
      performKeyExchange(
        "password".encodeUtf8(),
        "password".encodeUtf8()
      )
    assertKeysMatch(aliceKeysFirst, bobKeysFirst, true)

    // Second run
    val (aliceKeysSecond, bobKeysSecond) =
      performKeyExchange(
        "password".encodeUtf8(),
        "password".encodeUtf8()
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
