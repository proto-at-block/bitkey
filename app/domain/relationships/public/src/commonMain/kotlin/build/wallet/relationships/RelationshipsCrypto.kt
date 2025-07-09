package build.wallet.relationships

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import okio.ByteString

// TODO: remove suppress and V1 variants when V2 replaces V1
@Suppress("detekt:TooManyFunctions")
interface RelationshipsCrypto {
  /**
   * Generates a delegated decryption key pair.
   */
  fun generateDelegatedDecryptionKey(): Result<AppKey<DelegatedDecryptionKey>, RelationshipsCryptoError>

  /**
   * Generates an enrollment key pair for a protected customer.
   */
  fun generateProtectedCustomerEnrollmentPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerEnrollmentPakeKey>, RelationshipsCryptoError>

  /**
   * Generates a recovery key pair for a protected customer.
   */
  fun generateProtectedCustomerRecoveryPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerRecoveryPakeKey>, RelationshipsCryptoError>

  /**
   * Encrypts a delegated decryption key with a PAKE secure channel.
   */
  fun encryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
  ): Result<EncryptDelegatedDecryptionKeyOutput, RelationshipsCryptoError>

  /**
   * Decrypts the delegated decryption key with a PAKE secure channel.
   */
  fun decryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    encryptDelegatedDecryptionKeyOutput: EncryptDelegatedDecryptionKeyOutput,
  ): Result<PublicKey<DelegatedDecryptionKey>, RelationshipsCryptoError>

  /**
   * Generates a key certificate for a delegated decryption key.
   */
  suspend fun generateKeyCertificate(
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    hwAuthKey: HwAuthPublicKey,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<TrustedContactKeyCertificate, RelationshipsCryptoError>

  /**
   * Verifies a key certificate. Assumes that either the trustedHwEndorsementKey or the
   * trustedAppEndorsementKey has been validated. Requires at least 1 trusted key.
   */
  fun verifyKeyCertificate(
    keyCertificate: TrustedContactKeyCertificate,
    hwAuthKey: HwAuthPublicKey?,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<PublicKey<DelegatedDecryptionKey>, RelationshipsCryptoError>

  /**
   * Verify certificates using old keys and regenerate them using new keys.
   *
   * TODO: add unit tests
   */
  suspend fun verifyAndRegenerateKeyCertificate(
    oldCertificate: TrustedContactKeyCertificate,
    oldAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<TrustedContactKeyCertificate, RelationshipsCryptoError> =
    coroutineBinding {
      verifyKeyCertificate(
        keyCertificate = oldCertificate,
        hwAuthKey = oldHwAuthKey,
        appGlobalAuthKey = oldAppGlobalAuthKey
      ).bind()

      generateKeyCertificate(
        delegatedDecryptionKey = oldCertificate.delegatedDecryptionKey,
        hwAuthKey = oldHwAuthKey,
        appGlobalAuthKey = newAppGlobalAuthKey,
        appGlobalAuthKeyHwSignature = newAppGlobalAuthKeyHwSignature
      ).bind()
    }

  /**
   * Generic function to generate a key pair for a SocRec key.
   */
  fun <T> generateAsymmetricKey(): Result<AppKey<T>, RelationshipsCryptoError> where T : SocRecKey, T : CurveType.Curve25519

  /**
   * Encrypts the private key material by generating a random symmetric key,
   * the private key encryption key, and encrypting the private key material
   * with the private key encryption key. Returns the private key encryption
   * key and the encrypted private key material.
   */
  fun encryptPrivateKeyMaterial(
    privateKeyMaterial: ByteString,
  ): Result<EncryptPrivateKeyMaterialOutput, RelationshipsCryptoError>

  /**
   * Encrypts the descriptor with the input symmetric key and returns the ciphertext
   */
  fun encryptDescriptor(
    dek: PrivateKeyEncryptionKey,
    descriptor: ByteString,
  ): Result<XCiphertext, RelationshipsCryptoError>

  /**
   * Encrypts the private key encryption key with a shared secret derived from
   * the delegated decryption public key.
   */
  fun encryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, RelationshipsCryptoError>

  /**
   * Decrypt a private key encryption key using the delegated decryption key.
   *
   * This is used by trusted contacts to unseal a private key encryption key.
   * In recovery, this will be sent to the protected customer over a secure
   * channel using [transferPrivateKeyEncryptionKeyEncryption]
   * In Inheritance, this will be used to decrypt the private key material
   * to sign a transaction.
   */
  fun decryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): PrivateKeyEncryptionKey

  /**
   * Decrypts the private key encryption key and re-encrypt it with the PAKE
   * secure channel.
   *
   * This is used by the trusted contact to transfer their encrypted version
   * of the private key using the newly created PAKE secure channel
   * for recovery.
   */
  fun transferPrivateKeyEncryptionKeyEncryption(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, RelationshipsCryptoError>

  /**
   * Used by the protected customer to decrypt the private key material.
   */
  fun decryptPrivateKeyMaterial(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
    decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, RelationshipsCryptoError>

  fun decryptPrivateKeyMaterial(
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, RelationshipsCryptoError>
}

/**
 * Shortcut for [RelationshipsCrypto.verifyKeyCertificate] that uses the active hardware and app auth keys
 * to verify the certificate.
 */
fun RelationshipsCrypto.verifyKeyCertificate(
  account: FullAccount,
  keyCertificate: TrustedContactKeyCertificate,
): Result<PublicKey<DelegatedDecryptionKey>, RelationshipsCryptoError> =
  verifyKeyCertificate(
    keyCertificate = keyCertificate,
    hwAuthKey = account.keybox.activeHwKeyBundle.authKey,
    appGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey
  )

sealed class RelationshipsCryptoError : Error() {
  data class KeyGenerationFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data class EncryptionFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data class DecryptionFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data class KeyConfirmationFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data class KeyCertificateGenerationFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data class KeyCertificateVerificationFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data class ErrorGettingPrivateKey(val error: Throwable) : RelationshipsCryptoError()

  data class PakeChannelAcceptanceFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data class PakeChannelDecryptionFailed(override val cause: Throwable) : RelationshipsCryptoError()

  data object InvalidKeyType : RelationshipsCryptoError()

  data object PrivateKeyMissing : RelationshipsCryptoError()

  data object PublicKeyMissing : RelationshipsCryptoError()

  data object UnsupportedXCiphertextFormat : RelationshipsCryptoError()

  data object AuthKeysNotPresent : RelationshipsCryptoError()
}

data class EncryptPrivateKeyMaterialOutput(
  val privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  val sealedPrivateKeyMaterial: XCiphertext,
)

data class EncryptDelegatedDecryptionKeyOutput(
  val trustedContactEnrollmentPakeKey: PublicKey<TrustedContactEnrollmentPakeKey>,
  val keyConfirmation: ByteString,
  val sealedDelegatedDecryptionKey: XCiphertext,
)

data class DecryptPrivateKeyEncryptionKeyOutput(
  val trustedContactRecoveryPakeKey: PublicKey<TrustedContactRecoveryPakeKey>,
  val keyConfirmation: ByteString,
  val sealedPrivateKeyEncryptionKey: XCiphertext,
)
