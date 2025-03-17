package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import okio.ByteString
import okio.ByteString.Companion.toByteString
import build.wallet.rust.core.XChaCha20Poly1305 as CoreXChaCha20Poly1305

@BitkeyInject(AppScope::class)
class XChaCha20Poly1305Impl : XChaCha20Poly1305 {
  val tagLength = 16

  override fun encrypt(
    key: SymmetricKey,
    nonce: XNonce,
    plaintext: ByteString,
    aad: ByteString,
  ): XCiphertext {
    val cipher = CoreXChaCha20Poly1305(key.raw.toByteArray())
    val ciphertext =
      cipher.encrypt(
        nonce.bytes.toByteArray(),
        plaintext.toByteArray(),
        aad.toByteArray()
      )
    return XSealedData(
      XSealedData.Header(algorithm = XChaCha20Poly1305.ALGORITHM),
      ciphertext.toByteString(),
      nonce
    ).toOpaqueCiphertext()
  }

  override fun decrypt(
    key: SymmetricKey,
    ciphertextWithMetadata: XCiphertext,
    aad: ByteString,
  ): ByteString {
    val cipher = CoreXChaCha20Poly1305(key.raw.toByteArray())
    val sealedData = ciphertextWithMetadata.toXSealedData()
    return cipher.decrypt(
      sealedData.nonce.bytes.toByteArray(),
      sealedData.ciphertext.toByteArray(),
      aad.toByteArray()
    ).toByteString()
  }

  override fun encryptNoMetadata(
    key: SymmetricKey,
    plaintext: ByteString,
    nonce: ByteString,
    aad: ByteString,
  ): SealedData {
    val cipher = CoreXChaCha20Poly1305(key.raw.toByteArray())
    val ciphertextAndTag =
      cipher.encrypt(
        nonce.toByteArray(),
        plaintext.toByteArray(),
        aad.toByteArray()
      )

    // Split ciphertext and tag.
    val ciphertext = ciphertextAndTag.copyOfRange(0, ciphertextAndTag.size - tagLength)
    val tag = ciphertextAndTag.copyOfRange(ciphertextAndTag.size - tagLength, ciphertextAndTag.size)

    return SealedData(
      ciphertext = ciphertext.toByteString(),
      nonce = nonce,
      tag = tag.toByteString()
    )
  }

  override fun decryptNoMetadata(
    key: SymmetricKey,
    sealedData: SealedData,
    aad: ByteString,
  ): ByteString {
    val cipher = CoreXChaCha20Poly1305(key.raw.toByteArray())
    return cipher.decrypt(
      sealedData.nonce.toByteArray(),
      (sealedData.ciphertext.toByteArray() + sealedData.tag.toByteArray()),
      aad.toByteArray()
    ).toByteString()
  }
}
