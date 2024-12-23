package build.wallet.encrypt

import build.wallet.rust.core.CryptoBoxException
import build.wallet.rust.core.CryptoBoxKeyPairException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.experimental.xor

class CryptoBoxImplTests : FunSpec({
  val cryptoBox = CryptoBoxImpl()
  val nonceGenerator = XNonceGeneratorImpl()

  test("encrypt and decrypt") {
    val aliceKeyPair = cryptoBox.generateKeyPair()
    val bobKeyPair = cryptoBox.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = "Hello, world!".encodeUtf8()
    val sealedData = cryptoBox.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair.privateKey,
      nonce,
      plaintext
    )

    // Bob decrypts
    val decrypted = cryptoBox.decrypt(
      aliceKeyPair.publicKey,
      bobKeyPair.privateKey,
      sealedData
    )
    decrypted shouldBeEqual plaintext
  }

  test("empty plaintext") {
    val aliceKeyPair = cryptoBox.generateKeyPair()
    val bobKeyPair = cryptoBox.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = ByteString.EMPTY
    val sealedData = cryptoBox.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair.privateKey,
      nonce,
      plaintext
    )

    // Bob decrypts
    val decrypted = cryptoBox.decrypt(
      aliceKeyPair.publicKey,
      bobKeyPair.privateKey,
      sealedData
    )
    decrypted shouldBeEqual plaintext
  }

  test("bit flip") {
    val aliceKeyPair = cryptoBox.generateKeyPair()
    val bobKeyPair = cryptoBox.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = "Hello, world!".encodeUtf8()
    val sealedData = cryptoBox.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair.privateKey,
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
    shouldThrow<CryptoBoxException> {
      cryptoBox.decrypt(aliceKeyPair.publicKey, bobKeyPair.privateKey, modifiedSealedData)
    }
  }

  test("new from secret bytes") {
    val aliceKeyPair = cryptoBox.generateKeyPair()
    val keyPairFromBytes = cryptoBox.keypairFromSecretBytes(aliceKeyPair.privateKey.bytes)

    aliceKeyPair.publicKey shouldBeEqual keyPairFromBytes.publicKey
    aliceKeyPair.privateKey shouldBeEqual keyPairFromBytes.privateKey
  }

  test("invalid secret bytes") {
    val invalidSecretBytes = "invalid".encodeUtf8()
    shouldThrow<CryptoBoxKeyPairException> {
      cryptoBox.keypairFromSecretBytes(invalidSecretBytes)
    }
  }

  test("wrong key") {
    val aliceKeyPair = cryptoBox.generateKeyPair()
    val bobKeyPair = cryptoBox.generateKeyPair()

    // Alice encrypts
    val nonce = nonceGenerator.generateXNonce()
    val plaintext = "Hello, world!".encodeUtf8()
    val sealedData = cryptoBox.encrypt(
      bobKeyPair.publicKey,
      aliceKeyPair.privateKey,
      nonce,
      plaintext
    )

    // Bob attempts to decrypt the sealed data with the wrong key
    val charlieKeyPair = cryptoBox.generateKeyPair()
    shouldThrow<CryptoBoxException> {
      cryptoBox.decrypt(aliceKeyPair.publicKey, charlieKeyPair.privateKey, sealedData)
    }
  }
})
