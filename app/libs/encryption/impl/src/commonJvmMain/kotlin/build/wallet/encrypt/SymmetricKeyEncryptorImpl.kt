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
  override fun seal(
    unsealedData: ByteString,
    key: SymmetricKey,
  ): SealedData {
    require(key is SymmetricKeyImpl)
    val nonce = XNonceGeneratorImpl().generateXNonce()
    return XChaCha20Poly1305Impl().encryptNoMetadata(key, unsealedData, nonce.bytes)
  }

  override fun unseal(
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
      return XChaCha20Poly1305Impl().decryptNoMetadata(key, sealedData)
    }
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
