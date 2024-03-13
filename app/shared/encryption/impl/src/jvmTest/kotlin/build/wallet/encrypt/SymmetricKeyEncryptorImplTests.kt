package build.wallet.encrypt

import build.wallet.crypto.SymmetricKeyImpl
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

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

  test("sealed data from iOS should unseal on Android") {
    val key = "a01584980e6adfa1fc5671e0b35642964802e2ea83f65d0178b20e83c986520a".decodeHex()
    val sealed = SealedData(
      ciphertext = "9774e28e333ef24390d8d68d9d0b90ef56ced0dc531b5ee560abcc109eeb3d275812dad82e834c0fceea8ec9d3d1a34008ef505e103a7724d18aaab768b857efedafbd4e74a02ab6993a995a1bf321c8b2c825762f8dbd4d062be99114651f2e7f2f8439f5fe1585a2efde854ff26f268c9420e23500a9fd650c823edf22b250585f8ff95c8e8d".decodeHex(),
      nonce = "c7335a4a2cb943e7ff3c4a3d87a016e5d977e803c7989d9c".decodeHex(),
      tag = "e2b61dabf0857c5e71bc5231be5a8578".decodeHex()
    )
    symmetricKeyEncryptor.unseal(sealed, SymmetricKeyImpl(raw = key))
      .shouldBeEqual(
        "[b5435236/84'/1'/0']tprv8gw6bXR6ku6tCPZELTH5U5ioSn4k1rkn7Z4P6mWQf5wviG7zM9G6ZN99FXSqhZS77uBMpXzeBVywuA6Rw47k68cUX7N4ody212Ms2JdwFDU/0/*"
          .toByteArray()
          .toByteString()
      )
  }
})
