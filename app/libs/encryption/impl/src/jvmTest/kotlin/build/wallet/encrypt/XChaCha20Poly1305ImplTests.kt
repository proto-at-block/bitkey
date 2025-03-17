package build.wallet.encrypt

import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.rust.core.ChaCha20Poly1305Exception.DecryptException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.experimental.xor

class XChaCha20Poly1305ImplTests : FunSpec({
  val cipher = XChaCha20Poly1305Impl()

  test("encryption without AAD") {
    val key = SymmetricKeyGeneratorImpl().generate()
    val nonce = XNonceGeneratorImpl().generateXNonce()
    val plaintext = "Hello world!".encodeUtf8()

    val sealedData = cipher.encrypt(key, nonce, plaintext)
    val decrypted = cipher.decrypt(key, sealedData)
    plaintext.shouldBeEqual(decrypted)
  }

  test("encryption with AAD") {
    val key = SymmetricKeyGeneratorImpl().generate()
    val nonce = XNonceGeneratorImpl().generateXNonce()
    val plaintext = "Hello world!".encodeUtf8()
    val aad = "Lorem Ipsum".encodeUtf8()

    val sealedData = cipher.encrypt(key, nonce, plaintext, aad)
    val decrypted = cipher.decrypt(key, sealedData, aad)
    plaintext.shouldBeEqual(decrypted)
    shouldThrow<DecryptException> {
      cipher.decrypt(key, sealedData, "Wrong aad".encodeUtf8())
    }
    shouldThrow<DecryptException> {
      cipher.decrypt(key, sealedData)
    }
  }

  test("authentication") {
    val key = SymmetricKeyGeneratorImpl().generate()
    val nonce = XNonceGeneratorImpl().generateXNonce()
    val plaintext = "Hello world!".encodeUtf8()

    val cipherTextWithMetadata = cipher.encrypt(key, nonce, plaintext)
    val modifiedCiphertext =
      cipherTextWithMetadata.toXSealedData().ciphertext.toByteArray().apply {
        this[0] = this[0].xor(1)
      }.toByteString()
    val modifiedCipherTextWithMetadata =
      cipherTextWithMetadata.toXSealedData()
        .copy(ciphertext = modifiedCiphertext)
        .toOpaqueCiphertext()
    shouldThrow<DecryptException> {
      cipher.decrypt(key, modifiedCipherTextWithMetadata)
    }
  }

  /**
   * https://datatracker.ietf.org/doc/html/draft-arciszewski-xchacha-03#appendix-A.3.1
   */
  test("RFC vector") {
    val plaintext =
      (
        "4c616469657320616e642047656e746c656d656e206f662074686520636c6173" +
          "73206f66202739393a204966204920636f756c64206f6666657220796f75206f" +
          "6e6c79206f6e652074697020666f7220746865206675747572652c2073756e73" +
          "637265656e20776f756c642062652069742e"
      ).decodeHex()
    val aad = ("50515253c0c1c2c3c4c5c6c7").decodeHex()
    val key = ("808182838485868788898a8b8c8d8e8f909192939495969798999a9b9c9d9e9f").decodeHex()
    val iv = ("404142434445464748494a4b4c4d4e4f5051525354555657").decodeHex()
    val expectedCiphertext =
      (
        "bd6d179d3e83d43b9576579493c0e939572a1700252bfaccbed2902c21396cbb" +
          "731c7f1b0b4aa6440bf3a82f4eda7e39ae64c6708c54c216cb96b72e1213b452" +
          "2f8c9ba40db5d945b11b69b982c1bb9e3f3fac2bc369488f76b2383565d3fff9" +
          "21f9664c97637da9768812f615c68b13b52e"
      ).decodeHex()
    val expectedTag = ("c0875924c1c7987947deafd8780acf49").decodeHex()

    val out =
      cipher.encrypt(
        SymmetricKeyImpl(raw = key),
        XNonce(iv),
        plaintext,
        aad
      ).toXSealedData()
    val tagIndex = out.ciphertext.size - expectedTag.size
    val ciphertext = out.ciphertext.substring(0, tagIndex)
    val tag = out.ciphertext.substring(tagIndex)
    ciphertext.shouldBeEqual(expectedCiphertext)
    tag.shouldBeEqual(expectedTag)
  }
})
