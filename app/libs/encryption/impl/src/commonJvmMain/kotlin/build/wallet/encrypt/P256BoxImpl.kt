package build.wallet.encrypt

import build.wallet.crypto.PublicKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.toByteString
import build.wallet.rust.core.P256Box as CoreP256Box
import build.wallet.rust.core.P256BoxKeyPair as CoreP256BoxKeyPair

@BitkeyInject(AppScope::class)
class P256BoxImpl : P256Box {
  override fun generateKeyPair(): P256BoxKeyPair =
    CoreP256BoxKeyPair().let { keyPair: CoreP256BoxKeyPair ->
      P256BoxKeyPair(
        publicKey = P256BoxPublicKey(keyPair.publicKey().toByteString()),
        privateKey = P256BoxPrivateKey(keyPair.secretKey().toByteString())
      )
    }

  override fun keypairFromSecretBytes(secretBytes: ByteString): P256BoxKeyPair =
    CoreP256BoxKeyPair.fromSecretBytes(secretBytes.toByteArray()).let {
        keyPair: CoreP256BoxKeyPair ->
      P256BoxKeyPair(
        publicKey = P256BoxPublicKey(keyPair.publicKey().toByteString()),
        privateKey = P256BoxPrivateKey(keyPair.secretKey().toByteString())
      )
    }

  override fun encrypt(
    theirPublicKey: P256BoxPublicKey,
    myKeyPair: P256BoxKeyPair,
    nonce: XNonce,
    plaintext: ByteString,
  ): XCiphertext {
    val coreP256Box = CoreP256Box(
      theirPublicKey.bytes.toByteArray(),
      myKeyPair.privateKey.bytes.toByteArray()
    )
    val ciphertext = coreP256Box.encrypt(
      nonce.bytes.toByteArray(),
      plaintext.toByteArray()
    ).toByteString()

    return XSealedData(
      XSealedData.Header(algorithm = P256Box.ALGORITHM, format = XSealedData.Format.WithPubkey),
      ciphertext,
      nonce,
      PublicKey<P256BoxPublicKey>(myKeyPair.publicKey.bytes.hex())
    ).toOpaqueCiphertext()
  }

  override fun decrypt(
    myPrivateKey: P256BoxPrivateKey,
    sealedData: XCiphertext,
  ): ByteString {
    val xSealedData = sealedData.toXSealedData()
    if (xSealedData.header.algorithm != P256Box.ALGORITHM) {
      throw P256BoxError.InvalidAlgorithm
    }
    val theirPublicKey = xSealedData.publicKey
    if (theirPublicKey == null) {
      throw P256BoxError.MissingPublicKey
    }
    val coreP256Box = CoreP256Box(
      theirPublicKey.value.decodeHex().toByteArray(),
      myPrivateKey.bytes.toByteArray()
    )

    return coreP256Box.decrypt(
      xSealedData.nonce.bytes.toByteArray(),
      xSealedData.ciphertext.toByteArray()
    ).toByteString()
  }
}
