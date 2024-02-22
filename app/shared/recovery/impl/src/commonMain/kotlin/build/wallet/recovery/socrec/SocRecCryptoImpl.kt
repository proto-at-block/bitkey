package build.wallet.recovery.socrec

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.KeyCertificate
import build.wallet.bitkey.socrec.PrivateKeyEncryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentKey
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.catching
import build.wallet.crypto.CurveType
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.encrypt.Hkdf
import build.wallet.encrypt.Secp256k1KeyGenerator
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.Secp256k1SharedSecret
import build.wallet.encrypt.SymmetricKeyGenerator
import build.wallet.encrypt.XChaCha20Poly1305
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XNonceGenerator
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class SocRecCryptoImpl(
  private val secp256k1KeyGenerator: Secp256k1KeyGenerator,
  private val symmetricKeyGenerator: SymmetricKeyGenerator,
  private val xChaCha20Poly1305: XChaCha20Poly1305,
  private val secp256k1SharedSecret: Secp256k1SharedSecret,
  private val hkdf: Hkdf,
  private val xNonceGenerator: XNonceGenerator,
) : SocRecCrypto {
  companion object {
    const val PKMAT_AAD = "Bitkey Social Recovery PKMat Encryption Version 1.0"
    const val PKEK_INFO = "Bitkey Social Recovery PKEK Encryption Version 1.0"
    const val SS_INFO = "Bitkey Social Recovery PKEK Shared Secret Encryption Version 1.0"
    const val HKDF_OUTPUT_LENGTH = 32
  }

  override fun generateProtectedCustomerIdentityKey():
    Result<ProtectedCustomerIdentityKey, SocRecCryptoError> =
    Result.catching {
      ProtectedCustomerIdentityKey(generateAsymmetricKey())
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerEphemeralKey():
    Result<ProtectedCustomerEphemeralKey, SocRecCryptoError> =
    Result.catching {
      ProtectedCustomerEphemeralKey(generateAsymmetricKey())
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateTrustedContactIdentityKey():
    Result<TrustedContactIdentityKey, SocRecCryptoError> =
    Result.catching {
      TrustedContactIdentityKey(generateAsymmetricKey())
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerEnrollmentKey(
    password: ByteString,
  ): Result<ProtectedCustomerEnrollmentKey, SocRecCryptoError> {
    TODO("Not yet implemented")
  }

  override fun generateProtectedCustomerRecoveryKey(
    password: ByteString,
  ): Result<ProtectedCustomerRecoveryKey, SocRecCryptoError> {
    TODO("Not yet implemented")
  }

  override fun encryptTrustedContactIdentityKey(
    password: ByteString,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<EncryptTrustedContactIdentityKeyOutput, SocRecCryptoError> {
    TODO("Not yet implemented")
  }

  override fun decryptTrustedContactIdentityKey(
    password: ByteString,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    encryptTrustedContactIdentityKeyOutput: EncryptTrustedContactIdentityKeyOutput,
  ): Result<TrustedContactIdentityKey, SocRecCryptoError> {
    TODO("Not yet implemented")
  }

  override fun verifyKeyCertificate(
    keyCertificate: KeyCertificate,
    trustedHwEndorsementKey: HwAuthPublicKey?,
    trustedAppEndorsementKey: AppGlobalAuthPublicKey?,
  ): Result<TrustedContactIdentityKey, SocRecCryptoError> {
    TODO("Not yet implemented")
  }

  override fun generateKeyCertificate(
    trustedContactIdentityKey: TrustedContactIdentityKey,
    hwEndorsementKey: HwAuthPublicKey,
    appEndorsementKey: AppGlobalAuthKeypair,
    hwSignature: ByteString,
  ): Result<KeyCertificate, SocRecCryptoError> {
    TODO("Not yet implemented")
  }

  override fun <T : SocRecKey> generateAsymmetricKey(
    factory: (AppKey) -> T,
  ): Result<T, SocRecCryptoError> = Ok(factory(generateAsymmetricKey()))

  private fun generateAsymmetricKey(): AppKey {
    val (pubKey, privKey) = secp256k1KeyGenerator.generateKeypair()

    return AppKeyImpl(
      CurveType.SECP256K1,
      PublicKey(pubKey.value),
      PrivateKey(privKey.bytes)
    )
  }

  override fun encryptPrivateKeyMaterial(
    privateKeyMaterial: ByteString,
  ): Result<EncryptPrivateKeyMaterialOutput, SocRecCryptoError> {
    return Result.catching {
      // Step 1: Generate a private key encryption key
      val privateKeyEncryptionKey = PrivateKeyEncryptionKey(symmetricKeyGenerator.generate())

      // Step 2: Encrypt the private key material with the private key encryption key
      val sealedPrivateKeyMaterial =
        xChaCha20Poly1305.encrypt(
          key = privateKeyEncryptionKey,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = privateKeyMaterial,
          aad = PKMAT_AAD.encodeUtf8()
        )

      EncryptPrivateKeyMaterialOutput(privateKeyEncryptionKey, sealedPrivateKeyMaterial)
    }.mapError { SocRecCryptoError.EncryptionFailed(it) }
  }

  private fun <T : SocRecKey> T.getPrivateKey(): PrivateKey {
    val appKeyImpl = key as? AppKeyImpl ?: throw SocRecCryptoError.InvalidKeyType
    return appKeyImpl.privateKey ?: throw SocRecCryptoError.PrivateKeyMissing
  }

  override fun encryptPrivateKeyEncryptionKey(
    trustedContactIdentityKey: TrustedContactIdentityKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, SocRecCryptoError> {
    return Result.catching {
      // Step 1: Derive the identity shared secret from the identity keys
      val protectedIdentityCustomerPrivateKey =
        Secp256k1PrivateKey(protectedCustomerIdentityKey.getPrivateKey().bytes)
      val trustedContactIdentityPublicKey =
        Secp256k1PublicKey(trustedContactIdentityKey.publicKey.value)
      val sharedSecret =
        secp256k1SharedSecret.deriveSharedSecret(
          protectedIdentityCustomerPrivateKey,
          trustedContactIdentityPublicKey
        )

      // Step 2: HKDF the identity shared secret to derive the identity encryption key
      val sharedSecretKey =
        hkdf.deriveKey(
          ikm = sharedSecret,
          salt = null,
          info = PKEK_INFO.encodeUtf8(),
          outputLength = HKDF_OUTPUT_LENGTH
        )

      // Step 3: Encrypt the private key encryption key with the identity encryption key
      val aad =
        (
          protectedCustomerIdentityKey.publicKey.value.decodeHex().toByteArray() +
            trustedContactIdentityKey.publicKey.value.decodeHex().toByteArray()
        ).toByteString()
      val sealedPrivateKeyEncryptionKey =
        xChaCha20Poly1305.encrypt(
          key = sharedSecretKey,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = privateKeyEncryptionKey.raw,
          aad = aad
        )

      sealedPrivateKeyEncryptionKey
    }.mapError { SocRecCryptoError.EncryptionFailed(it) }
  }

  override fun deriveAndEncryptSharedSecret(
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<XCiphertext, SocRecCryptoError> {
    return Result.catching {
      // Step 1: Derive the identity shared secret from the identity keys
      val trustedContactIdentityPrivateKey =
        Secp256k1PrivateKey(trustedContactIdentityKey.getPrivateKey().bytes)
      val protectedCustomerIdentityPublicKey =
        Secp256k1PublicKey(protectedCustomerIdentityKey.publicKey.value)
      val identitySharedSecret =
        secp256k1SharedSecret.deriveSharedSecret(
          trustedContactIdentityPrivateKey,
          protectedCustomerIdentityPublicKey
        )

      // Step 2: HKDF the identity shared secret to derive the identity encryption key
      val identitySharedSecretKey =
        hkdf.deriveKey(
          ikm = identitySharedSecret,
          salt = null,
          info = PKEK_INFO.encodeUtf8(),
          outputLength = HKDF_OUTPUT_LENGTH
        )

      // Step 3: Derive the ephemeral shared secret from the PC ephemeral and TC identity keys
      val protectedCustomerEphemeralPublicKey =
        Secp256k1PublicKey(protectedCustomerEphemeralKey.publicKey.value)
      val ephemeralSharedSecret =
        secp256k1SharedSecret.deriveSharedSecret(
          trustedContactIdentityPrivateKey,
          protectedCustomerEphemeralPublicKey
        )

      // Step 4: HKDF the ephemeral shared secret to derive the ephemeral encryption key
      val ephemeralSharedSecretKey =
        hkdf.deriveKey(
          ikm = ephemeralSharedSecret,
          salt = null,
          info = SS_INFO.encodeUtf8(),
          outputLength = HKDF_OUTPUT_LENGTH
        )

      // Step 5: Encrypt the identity encryption key with the ephemeral encryption key
      val aad =
        (
          protectedCustomerIdentityKey.publicKey.value.decodeHex().toByteArray() +
            trustedContactIdentityKey.publicKey.value.decodeHex().toByteArray()
        ).toByteString()
      val sealedSharedSecret =
        xChaCha20Poly1305.encrypt(
          key = ephemeralSharedSecretKey,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = identitySharedSecretKey.raw,
          aad = aad
        )

      sealedSharedSecret
    }.mapError { SocRecCryptoError.SharedSecretEncryptionFailed(it) }
  }

  override fun decryptPrivateKeyEncryptionKey(
    password: ByteString,
    protectedCustomerRecoveryKey: ProtectedCustomerRecoveryKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, SocRecCryptoError> {
    TODO("Not yet implemented")
  }

  override fun decryptPrivateKeyMaterial(
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    sealedPrivateKeyMaterial: XCiphertext,
    secureChannelData: DecryptPrivateKeyMaterialParams,
  ): Result<ByteString, SocRecCryptoError> {
    return when (secureChannelData) {
      is DecryptPrivateKeyMaterialParams.V1 -> {
        decryptPrivateKeyMaterialV1(
          secureChannelData.sharedSecretCipherText,
          protectedCustomerIdentityKey,
          secureChannelData.protectedCustomerEphemeralKey,
          trustedContactIdentityKey,
          secureChannelData.sealedPrivateKeyEncryptionKey,
          sealedPrivateKeyMaterial
        )
      }
      is DecryptPrivateKeyMaterialParams.V2 -> {
        Err(
          SocRecCryptoError.DecryptionFailed(
            UnsupportedOperationException("Version 2 not supported")
          )
        )
      }
    }
  }

  private fun decryptPrivateKeyMaterialV1(
    sharedSecretCipherText: XCiphertext,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    sealedPrivateKeyEncryptionKey: XCiphertext,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, SocRecCryptoError> {
    return Result.catching {
      // Step 1: Derive the ephemeral shared secret from the PC ephemeral and TC identity keys
      val protectedCustomerEphemeralPrivateKey =
        Secp256k1PrivateKey(protectedCustomerEphemeralKey.getPrivateKey().bytes)
      val trustedContactIdentityPublicKey =
        Secp256k1PublicKey(trustedContactIdentityKey.publicKey.value)
      val ephemeralSharedSecret =
        secp256k1SharedSecret.deriveSharedSecret(
          protectedCustomerEphemeralPrivateKey,
          trustedContactIdentityPublicKey
        )

      // Step 2: HKDF the ephemeral shared secret to derive the ephemeral encryption key
      val ephemeralSharedSecretKey =
        hkdf.deriveKey(
          ikm = ephemeralSharedSecret,
          salt = null,
          info = SS_INFO.encodeUtf8(),
          outputLength = HKDF_OUTPUT_LENGTH
        )

      // Step 3: Decrypt the identity encryption key with the ephemeral encryption key
      val pkekAad =
        (
          protectedCustomerIdentityKey.publicKey.value.decodeHex().toByteArray() +
            trustedContactIdentityKey.publicKey.value.decodeHex().toByteArray()
        ).toByteString()
      val identitySharedSecretKey =
        xChaCha20Poly1305.decrypt(
          key = ephemeralSharedSecretKey,
          ciphertextWithMetadata = sharedSecretCipherText,
          aad = pkekAad
        )

      // Step 4: Decrypt the private key encryption key with the identity encryption key
      val privateKeyEncryptionKey =
        xChaCha20Poly1305.decrypt(
          key = SymmetricKeyImpl(raw = identitySharedSecretKey),
          ciphertextWithMetadata = sealedPrivateKeyEncryptionKey,
          aad = pkekAad
        )

      // Step 5: Decrypt the private key material with the private key encryption key
      val privateKeyMaterial =
        xChaCha20Poly1305.decrypt(
          key = SymmetricKeyImpl(raw = privateKeyEncryptionKey),
          ciphertextWithMetadata = sealedPrivateKeyMaterial,
          aad = PKMAT_AAD.encodeUtf8()
        )

      privateKeyMaterial
    }.mapError { SocRecCryptoError.DecryptionFailed(it) }
  }
}
