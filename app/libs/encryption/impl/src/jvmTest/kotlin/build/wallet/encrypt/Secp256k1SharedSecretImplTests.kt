package build.wallet.encrypt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual

class Secp256k1SharedSecretImplTests : FunSpec({

  val sharedSecret = Secp256k1SharedSecretImpl()
  val secp256k1KeyGenerator = Secp256k1KeyGeneratorImpl()

  test("deriveSharedSecret") {
    val aliceKeypair = secp256k1KeyGenerator.generateKeypair()
    val alicePrivateKey = aliceKeypair.privateKey
    val alicePublicKey = aliceKeypair.publicKey
    val bobKeypair = secp256k1KeyGenerator.generateKeypair()
    val bobPrivateKey = bobKeypair.privateKey
    val bobPublicKey = bobKeypair.publicKey

    val aliceSharedSecretBytes = sharedSecret.deriveSharedSecret(alicePrivateKey, bobPublicKey)
    val bobSharedSecretBytes = sharedSecret.deriveSharedSecret(bobPrivateKey, alicePublicKey)
    val oddShardSecretBytes = sharedSecret.deriveSharedSecret(alicePrivateKey, alicePublicKey)
    aliceSharedSecretBytes.shouldBeEqual(bobSharedSecretBytes)
    aliceSharedSecretBytes.shouldNotBeEqual(oddShardSecretBytes)
  }
})
