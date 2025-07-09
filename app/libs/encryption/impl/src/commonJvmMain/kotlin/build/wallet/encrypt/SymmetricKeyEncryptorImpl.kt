package build.wallet.encrypt

import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import okio.ByteString
import okio.ByteString.Companion.toByteString
import javax.crypto.Cipher
import javax.crypto.Cipher.DECRYPT_MODE
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@BitkeyInject(AppScope::class)
class SymmetricKeyEncryptorImpl : SymmetricKeyEncryptor {
  private val xChaCha20Poly1305 = XChaCha20Poly1305Impl()
  private val xNonceGenerator = XNonceGeneratorImpl()

  override fun sealNoMetadata(
    unsealedData: ByteString,
    key: SymmetricKey,
  ): SealedData {
    require(key is SymmetricKeyImpl)
    val nonce = xNonceGenerator.generateXNonce()
    return xChaCha20Poly1305.encryptNoMetadata(key, unsealedData, nonce.bytes)
  }

  override fun unsealNoMetadata(
    sealedData: SealedData,
    key: SymmetricKey,
  ): ByteString {
    require(key is SymmetricKeyImpl)
    // TODO: remove aes when all backups are migrated to XChaCha20Poly1305
    if (sealedData.nonce.size != 24) {
      val cipher =
        aesGcmCipher(
          mode = DECRYPT_MODE,
          nonce = sealedData.nonce,
          key = key
        )
      val plaintext = cipher.doFinal(sealedData.ciphertext.toByteArray())
      return plaintext.toByteString()
    } else {
      return xChaCha20Poly1305.decryptNoMetadata(key, sealedData)
    }
  }

  override fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
    aad: ByteString,
  ): XCiphertext {
    return xChaCha20Poly1305.encrypt(
      key = key,
      nonce = xNonceGenerator.generateXNonce(),
      plaintext = unsealedData,
      aad = aad
    )
  }

  override fun unseal(
    ciphertext: XCiphertext,
    key: SymmetricKey,
    aad: ByteString,
  ): ByteString {
    return xChaCha20Poly1305.decrypt(
      key = key,
      ciphertextWithMetadata = ciphertext,
      aad = aad
    )
  }

  private fun aesGcmCipher(
    mode: Int,
    nonce: ByteString,
    key: SymmetricKeyImpl,
  ): Cipher {
    val cipher = Cipher.getInstance(TRANSFORMATION)
    val authTagLength = 128
    val gcmSpec = GCMParameterSpec(authTagLength, nonce.toByteArray())
    val rawKey = key.raw
    val secretKey = SecretKeySpec(rawKey.toByteArray(), SymmetricKeyImpl.ALGORITHM)
    cipher.init(mode, secretKey, gcmSpec)
    return cipher
  }

  private companion object {
    const val ALGORITHM = "AES"
    const val BLOCK_MODE = "GCM"
    const val PADDING = "NoPadding"

    // AES/GCM/NoPadding
    const val TRANSFORMATION = "$ALGORITHM/$BLOCK_MODE/$PADDING"
  }
}
