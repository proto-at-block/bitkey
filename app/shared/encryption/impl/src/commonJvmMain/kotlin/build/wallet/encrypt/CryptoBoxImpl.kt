package build.wallet.encrypt

import okio.ByteString
import okio.ByteString.Companion.toByteString
import build.wallet.rust.core.CryptoBox as CoreCryptoBox
import build.wallet.rust.core.CryptoBoxKeyPair as CoreCryptoBoxKeyPair

class CryptoBoxImpl : CryptoBox {
  override fun generateKeyPair(): CryptoBoxKeyPair =
    CoreCryptoBoxKeyPair().let { keyPair: CoreCryptoBoxKeyPair ->
      CryptoBoxKeyPair(
        publicKey = CryptoBoxPublicKey(keyPair.publicKey().toByteString()),
        privateKey = CryptoBoxPrivateKey(keyPair.secretKey().toByteString())
      )
    }

  override fun encrypt(
    theirPublicKey: CryptoBoxPublicKey,
    myPrivateKey: CryptoBoxPrivateKey,
    nonce: XNonce,
    plaintext: ByteString,
  ): XCiphertext {
    val coreCryptoBox = CoreCryptoBox(
      theirPublicKey.bytes.toByteArray(),
      myPrivateKey.bytes.toByteArray()
    )
    val ciphertext = coreCryptoBox.encrypt(
      nonce.bytes.toByteArray(),
      plaintext.toByteArray()
    ).toByteString()

    return XSealedData(
      XSealedData.Header(algorithm = CryptoBox.ALGORITHM),
      ciphertext,
      nonce
    ).toOpaqueCiphertext()
  }

  override fun decrypt(
    theirPublicKey: CryptoBoxPublicKey,
    myPrivateKey: CryptoBoxPrivateKey,
    sealedData: XCiphertext,
  ): ByteString {
    val xSealedData = sealedData.toXSealedData()
    if (xSealedData.header.algorithm != CryptoBox.ALGORITHM) {
      throw CryptoBoxError.InvalidAlgorithm
    }
    val coreCryptoBox = CoreCryptoBox(
      theirPublicKey.bytes.toByteArray(),
      myPrivateKey.bytes.toByteArray()
    )

    return coreCryptoBox.decrypt(
      xSealedData.nonce.bytes.toByteArray(),
      xSealedData.ciphertext.toByteArray()
    ).toByteString()
  }
}
