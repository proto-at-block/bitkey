package build.wallet.encrypt

import build.wallet.rust.core.P256BoxException
import build.wallet.rust.core.P256BoxKeyPairException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.experimental.xor

class P256BoxImplTests : FunSpec({
  val p256Box = P256BoxImpl()
  val nonceGenerator = XNonceGeneratorImpl()

  test("encrypt and decrypt") {
    val aliceKeyPair = p256Box.generateKeyPair()
    val bobKeyPair = p256Box.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = "Hello, world!".encodeUtf8()
    val sealedData = p256Box.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair,
      nonce,
      plaintext
    )

    // Bob decrypts
    val decrypted = p256Box.decrypt(
      bobKeyPair.privateKey,
      sealedData
    )
    decrypted shouldBeEqual plaintext
  }

  test("empty plaintext") {
    val aliceKeyPair = p256Box.generateKeyPair()
    val bobKeyPair = p256Box.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = ByteString.EMPTY
    val sealedData = p256Box.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair,
      nonce,
      plaintext
    )

    // Bob decrypts
    val decrypted = p256Box.decrypt(
      bobKeyPair.privateKey,
      sealedData
    )
    decrypted shouldBeEqual plaintext
  }

  test("bit flip") {
    val aliceKeyPair = p256Box.generateKeyPair()
    val bobKeyPair = p256Box.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = "Hello, world!".encodeUtf8()
    val sealedData = p256Box.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair,
      nonce,
      plaintext
    )

    // Flip a bit in the sealed data
    val modifiedCiphertext = sealedData.toXSealedData().ciphertext.toByteArray().apply {
      this[0] = this[0] xor 1
    }.toByteString()
    val modifiedSealedData =
      sealedData.toXSealedData()
        .copy(ciphertext = modifiedCiphertext)
        .toOpaqueCiphertext()

    // Bob attempts to decrypt
    shouldThrow<P256BoxException> {
      p256Box.decrypt(bobKeyPair.privateKey, modifiedSealedData)
    }
  }

  test("new from secret bytes") {
    val aliceKeyPair = p256Box.generateKeyPair()
    val keyPairFromBytes = p256Box.keypairFromSecretBytes(aliceKeyPair.privateKey.bytes)

    aliceKeyPair.publicKey shouldBeEqual keyPairFromBytes.publicKey
    aliceKeyPair.privateKey shouldBeEqual keyPairFromBytes.privateKey
  }

  test("invalid secret bytes") {
    val invalidSecretBytes = "invalid".encodeUtf8()
    shouldThrow<P256BoxKeyPairException> {
      p256Box.keypairFromSecretBytes(invalidSecretBytes)
    }
  }

  test("wrong key") {
    val aliceKeyPair = p256Box.generateKeyPair()
    val bobKeyPair = p256Box.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = "Hello, world!".encodeUtf8()
    val sealedData = p256Box.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair,
      nonce,
      plaintext
    )

    // Bob attempts to decrypt the sealed data with the wrong key
    val charlieKeyPair = p256Box.generateKeyPair()
    shouldThrow<P256BoxException> {
      p256Box.decrypt(charlieKeyPair.privateKey, sealedData)
    }
  }
})
