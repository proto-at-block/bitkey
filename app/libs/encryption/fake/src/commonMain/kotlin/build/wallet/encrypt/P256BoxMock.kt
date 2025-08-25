package build.wallet.encrypt

import okio.ByteString
import okio.ByteString.Companion.encodeUtf8

data class P256BoxEncryptCall(
  val theirPublicKey: P256BoxPublicKey,
  val myPrivateKey: P256BoxPrivateKey,
  val nonce: XNonce,
  val plaintext: ByteString,
)

class P256BoxMock : P256Box {
  private val defaultKeyPair = P256BoxKeyPair(
    privateKey = P256BoxPrivateKey("default-private-key".encodeUtf8()),
    publicKey = P256BoxPublicKey("default-public-key".encodeUtf8())
  )

  val testCiphertext = XCiphertext("test-ciphertext-value")

  var encryptResult: XCiphertext = testCiphertext
  var generateKeyPairResult: P256BoxKeyPair = defaultKeyPair

  val encryptCalls = mutableListOf<P256BoxEncryptCall>()
  val generateKeyPairCalls = mutableListOf<Unit>()
  val keypairFromSecretBytesCalls = mutableListOf<ByteString>()
  val decryptCalls = mutableListOf<Pair<P256BoxPrivateKey, XCiphertext>>()

  override fun generateKeyPair(): P256BoxKeyPair {
    generateKeyPairCalls.add(Unit)
    return generateKeyPairResult
  }

  override fun keypairFromSecretBytes(secretBytes: ByteString): P256BoxKeyPair {
    keypairFromSecretBytesCalls.add(secretBytes)
    return generateKeyPairResult
  }

  override fun encrypt(
    theirPublicKey: P256BoxPublicKey,
    myKeyPair: P256BoxKeyPair,
    nonce: XNonce,
    plaintext: ByteString,
  ): XCiphertext {
    encryptCalls.add(
      P256BoxEncryptCall(
        theirPublicKey = theirPublicKey,
        myPrivateKey = myKeyPair.privateKey,
        nonce = nonce,
        plaintext = plaintext
      )
    )
    return encryptResult
  }

  override fun decrypt(
    myPrivateKey: P256BoxPrivateKey,
    sealedData: XCiphertext,
  ): ByteString {
    decryptCalls.add(Pair(myPrivateKey, sealedData))
    return "decrypted-data".encodeUtf8()
  }

  fun reset() {
    encryptResult = testCiphertext
    generateKeyPairResult = defaultKeyPair
    encryptCalls.clear()
    generateKeyPairCalls.clear()
    keypairFromSecretBytesCalls.clear()
    decryptCalls.clear()
  }
}
