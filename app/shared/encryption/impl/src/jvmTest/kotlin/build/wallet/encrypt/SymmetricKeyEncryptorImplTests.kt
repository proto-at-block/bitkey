package build.wallet.encrypt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okio.ByteString.Companion.encodeUtf8

class SymmetricKeyEncryptorImplTests : FunSpec({

  val symmetricKeyEncryptor = SymmetricKeyEncryptorImpl()
  val symmetricKeyGenerator = SymmetricKeyGeneratorImpl()

  test("seal and unseal should return original data") {
    val key = symmetricKeyGenerator.generate()

    val originalData = "13 characters".encodeUtf8()
    val sealedData = symmetricKeyEncryptor.seal(originalData, key)
    val unsealedData = symmetricKeyEncryptor.unseal(sealedData, key)
    unsealedData shouldBe originalData
  }

  test("sealing the same data with different nonces should produce different ciphertexts") {
    val key = symmetricKeyGenerator.generate()
    val data = "Repeatable test data".encodeUtf8()

    // Seal the data twice, with different nonces each time
    val sealedData1 = symmetricKeyEncryptor.seal(data, key)
    val sealedData2 = symmetricKeyEncryptor.seal(data, key)

    // The sealed (ciphertext) data should not be the same, due to different nonces
    sealedData1.ciphertext shouldNotBe sealedData2.ciphertext
  }
})
