package build.wallet.recovery.socrec

import build.wallet.bitkey.app.AppGlobalAuthKeypair
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.KeyCertificate
import build.wallet.bitkey.socrec.PrivateKeyEncryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentKey
import build.wallet.bitkey.socrec.ProtectedCustomerEphemeralKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TrustedContactEnrollmentKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.bitkey.socrec.TrustedContactRecoveryKey
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Result
import okio.ByteString

// TODO: remove suppress and V1 variants when V2 replaces V1
@Suppress("detekt:TooManyFunctions")
interface SocRecCrypto {
  /**
   * Generates an identity key pair for a protected customer.
   */
  fun generateProtectedCustomerIdentityKey(): Result<
    ProtectedCustomerIdentityKey,
    SocRecCryptoError
  >

  /**
   * Generates an ephemeral key pair for a protected customer.
   */
  fun generateProtectedCustomerEphemeralKey():
    Result<ProtectedCustomerEphemeralKey, SocRecCryptoError>

  /**
   * Generates an identity key pair for a trusted contact.
   */
  fun generateTrustedContactIdentityKey(): Result<TrustedContactIdentityKey, SocRecCryptoError>

  /**
   * Generates an enrollment key pair for a protected customer.
   */
  fun generateProtectedCustomerEnrollmentKey(
    password: ByteString,
  ): Result<ProtectedCustomerEnrollmentKey, SocRecCryptoError>

  /**
   * Generates a recovery key pair for a protected customer.
   */
  fun generateProtectedCustomerRecoveryKey(
    password: ByteString,
  ): Result<ProtectedCustomerRecoveryKey, SocRecCryptoError>

  /**
   * Encrypts a trusted contact identity key with a PAKE secure channel.
   */
  fun encryptTrustedContactIdentityKey(
    password: ByteString,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<EncryptTrustedContactIdentityKeyOutput, SocRecCryptoError>

  /**
   * Decrypts the trusted contact identity key with a PAKE secure channel.
   */
  fun decryptTrustedContactIdentityKey(
    password: ByteString,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    encryptTrustedContactIdentityKeyOutput: EncryptTrustedContactIdentityKeyOutput,
  ): Result<TrustedContactIdentityKey, SocRecCryptoError>

  /**
   * Generates a key certificate for a trusted contact identity key.
   */
  fun generateKeyCertificate(
    trustedContactIdentityKey: TrustedContactIdentityKey,
    hwEndorsementKey: HwAuthPublicKey,
    appEndorsementKey: AppGlobalAuthKeypair,
    hwSignature: ByteString,
  ): Result<KeyCertificate, SocRecCryptoError>

  /**
   * Verifies a key certificate. Assumes that either the trustedHwEndorsementKey or the
   * trustedAppEndorsementKey has been validated. Requires at least 1 trusted key.
   */
  fun verifyKeyCertificate(
    keyCertificate: KeyCertificate,
    trustedHwEndorsementKey: HwAuthPublicKey? = null,
    trustedAppEndorsementKey: AppGlobalAuthPublicKey? = null,
  ): Result<TrustedContactIdentityKey, SocRecCryptoError>

  /**
   * Generic function to generate a key pair for a SocRec key.
   */
  fun <T : SocRecKey> generateAsymmetricKey(factory: (AppKey) -> T): Result<T, SocRecCryptoError>

  /**
   * Encrypts the private key material by generating a random symmetric key,
   * the private key encryption key, and encrypting the private key material
   * with the private key encryption key. Returns the private key encryption
   * key and the encrypted private key material.
   */
  fun encryptPrivateKeyMaterial(
    privateKeyMaterial: ByteString,
  ): Result<EncryptPrivateKeyMaterialOutput, SocRecCryptoError>

  /**
   * Encrypts the private key encryption key with a shared secret derived from
   * the protected customer's identity private key and the trusted contact's
   * identity public key.
   */
  fun encryptPrivateKeyEncryptionKey(
    trustedContactIdentityKey: TrustedContactIdentityKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, SocRecCryptoError>

  /**
   * Used by the trusted contact to (1) derive the private key encryption key
   * shared secret with the trusted contact's identity private key and the
   * protected customer's identity public key, and (2) encrypt that private key
   * encryption shared secret with a secure channel shared secret derived from
   * the protected customer's ephemeral public key and the trusted contact's
   * identity private key.
   */
  fun deriveAndEncryptSharedSecret(
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<XCiphertext, SocRecCryptoError>

  /**
   * Used by the trusted contact to decrypt the private key encryption key and re-encrypt it with
   * the PAKE secure channel.
   */
  fun decryptPrivateKeyEncryptionKey(
    password: ByteString,
    protectedCustomerRecoveryKey: ProtectedCustomerRecoveryKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, SocRecCryptoError>

  /**
   * Used by the protected customer to decrypt the private key material. The decryption parameters
   * depend on whether v1 of the protocol (pre-PAKE) or v2 (PAKE) is used.
   */
  fun decryptPrivateKeyMaterial(
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    sealedPrivateKeyMaterial: XCiphertext,
    secureChannelData: DecryptPrivateKeyMaterialParams,
  ): Result<ByteString, SocRecCryptoError>
}

sealed class SocRecCryptoError : Error() {
  data class KeyGenerationFailed(override val cause: Throwable) : SocRecCryptoError()

  data class EncryptionFailed(override val cause: Throwable) : SocRecCryptoError()

  data class DecryptionFailed(override val cause: Throwable) : SocRecCryptoError()

  data class KeyConfirmationFailed(override val cause: Throwable) : SocRecCryptoError()

  data class KeyCertificateVerificationFailed(override val cause: Throwable) : SocRecCryptoError()

  data class SharedSecretEncryptionFailed(override val cause: Throwable) : SocRecCryptoError()

  data object InvalidKeyType : SocRecCryptoError()

  data object PrivateKeyMissing : SocRecCryptoError()
}

data class EncryptPrivateKeyMaterialOutput(
  val privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  val sealedPrivateKeyMaterial: XCiphertext,
)

data class EncryptTrustedContactIdentityKeyOutput(
  val trustedContactEnrollmentKey: TrustedContactEnrollmentKey,
  val keyConfirmation: ByteString,
  val sealedTrustedContactIdentityKey: XCiphertext,
)

data class DecryptPrivateKeyEncryptionKeyOutput(
  val trustedContactRecoveryKey: TrustedContactRecoveryKey,
  val keyConfirmation: ByteString,
  val sealedPrivateKeyEncryptionKey: XCiphertext,
)

sealed class DecryptPrivateKeyMaterialParams {
  data class V1(
    val sharedSecretCipherText: XCiphertext,
    val protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    val sealedPrivateKeyEncryptionKey: XCiphertext,
  ) : DecryptPrivateKeyMaterialParams()

  data class V2(
    val password: ByteString,
    val protectedCustomerRecoveryKey: ProtectedCustomerRecoveryKey,
    val decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
  ) : DecryptPrivateKeyMaterialParams()
}
