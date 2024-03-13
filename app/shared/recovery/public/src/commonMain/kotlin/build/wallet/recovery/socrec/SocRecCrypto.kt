package build.wallet.recovery.socrec

import build.wallet.bitkey.account.FullAccount
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.PrivateKeyEncryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.bitkey.socrec.TrustedContactKeyCertificate
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.encrypt.XCiphertext
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import okio.ByteString

// TODO: remove suppress and V1 variants when V2 replaces V1
@Suppress("detekt:TooManyFunctions")
interface SocRecCrypto {
  /**
   * Generates a delegated decryption key pair.
   */
  fun generateDelegatedDecryptionKey(): Result<DelegatedDecryptionKey, SocRecCryptoError>

  /**
   * Generates an enrollment key pair for a protected customer.
   */
  fun generateProtectedCustomerEnrollmentPakeKey(
    password: PakeCode,
  ): Result<ProtectedCustomerEnrollmentPakeKey, SocRecCryptoError>

  /**
   * Generates a recovery key pair for a protected customer.
   */
  fun generateProtectedCustomerRecoveryPakeKey(
    password: PakeCode,
  ): Result<ProtectedCustomerRecoveryPakeKey, SocRecCryptoError>

  /**
   * Encrypts a delegated decryption key with a PAKE secure channel.
   */
  fun encryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: ProtectedCustomerEnrollmentPakeKey,
    delegatedDecryptionKey: DelegatedDecryptionKey,
  ): Result<EncryptDelegatedDecryptionKeyOutput, SocRecCryptoError>

  /**
   * Decrypts the delegated decryption key with a PAKE secure channel.
   */
  fun decryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: ProtectedCustomerEnrollmentPakeKey,
    encryptDelegatedDecryptionKeyOutput: EncryptDelegatedDecryptionKeyOutput,
  ): Result<DelegatedDecryptionKey, SocRecCryptoError>

  /**
   * Generates a key certificate for a delegated decryption key.
   */
  suspend fun generateKeyCertificate(
    delegatedDecryptionKey: DelegatedDecryptionKey,
    hwAuthKey: HwAuthPublicKey,
    appGlobalAuthKey: AppGlobalAuthPublicKey,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<TrustedContactKeyCertificate, SocRecCryptoError>

  /**
   * Verifies a key certificate. Assumes that either the trustedHwEndorsementKey or the
   * trustedAppEndorsementKey has been validated. Requires at least 1 trusted key.
   */
  fun verifyKeyCertificate(
    keyCertificate: TrustedContactKeyCertificate,
    hwAuthKey: HwAuthPublicKey?,
    appGlobalAuthKey: AppGlobalAuthPublicKey?,
  ): Result<DelegatedDecryptionKey, SocRecCryptoError>

  /**
   * Verify certificates using old keys and regenerate them using new keys.
   *
   * TODO: add unit tests
   */
  suspend fun verifyAndRegenerateKeyCertificate(
    oldCertificate: TrustedContactKeyCertificate,
    oldAppGlobalAuthKey: AppGlobalAuthPublicKey?,
    oldHwAuthKey: HwAuthPublicKey,
    newAppGlobalAuthKey: AppGlobalAuthPublicKey,
    newAppGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<TrustedContactKeyCertificate, SocRecCryptoError> =
    binding {
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
  fun <T : SocRecKey> generateAsymmetricKey(factory: (AppKey) -> T): Result<T, SocRecCryptoError>

  /**
   * Encrypts the private key material by generating a random symmetric key,
   * the private key encryption key, and encrypting the private key material
   * with the private key encryption key. Returns the private key encryption
   * key and the encrypted private key material.
   */

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
   * the delegated decryption public key.
   */

  /**
   * Encrypts the private key encryption key with a shared secret derived from
   * the delegated decryption public key.
   */
  fun encryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: DelegatedDecryptionKey,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, SocRecCryptoError>

  /**
   * Used by the trusted contact to decrypt the private key encryption key and re-encrypt it with
   * the PAKE secure channel.
   */

  /**
   * Used by the trusted contact to decrypt the private key encryption key and re-encrypt it with
   * the PAKE secure channel.
   */
  fun decryptPrivateKeyEncryptionKey(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: ProtectedCustomerRecoveryPakeKey,
    delegatedDecryptionKey: DelegatedDecryptionKey,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, SocRecCryptoError>

  /**
   * Used by the protected customer to decrypt the private key material.
   */

  /**
   * Used by the protected customer to decrypt the private key material.
   */
  fun decryptPrivateKeyMaterial(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: ProtectedCustomerRecoveryPakeKey,
    decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, SocRecCryptoError>
}

/**
 * Shortcut for [SocRecCrypto.verifyKeyCertificate] that uses the active hardware and app auth keys
 * to verify the certificate.
 */
fun SocRecCrypto.verifyKeyCertificate(
  account: FullAccount,
  keyCertificate: TrustedContactKeyCertificate,
): Result<DelegatedDecryptionKey, SocRecCryptoError> =
  verifyKeyCertificate(
    keyCertificate = keyCertificate,
    hwAuthKey = account.keybox.activeHwKeyBundle.authKey,
    appGlobalAuthKey = account.keybox.activeAppKeyBundle.authKey
  )

sealed class SocRecCryptoError : Error() {
  data class KeyGenerationFailed(override val cause: Throwable) : SocRecCryptoError()

  data class EncryptionFailed(override val cause: Throwable) : SocRecCryptoError()

  data class DecryptionFailed(override val cause: Throwable) : SocRecCryptoError()

  data class KeyConfirmationFailed(override val cause: Throwable) : SocRecCryptoError()

  data class KeyCertificateGenerationFailed(override val cause: Throwable) : SocRecCryptoError()

  data class KeyCertificateVerificationFailed(override val cause: Throwable) : SocRecCryptoError()

  data class ErrorGettingPrivateKey(val error: Throwable) : SocRecCryptoError()

  data class PakeChannelAcceptanceFailed(override val cause: Throwable) : SocRecCryptoError()

  data class PakeChannelDecryptionFailed(override val cause: Throwable) : SocRecCryptoError()

  data object InvalidKeyType : SocRecCryptoError()

  data object PrivateKeyMissing : SocRecCryptoError()

  data object PublicKeyMissing : SocRecCryptoError()

  data object UnsupportedXCiphertextVersion : SocRecCryptoError()

  data object AuthKeysNotPresent : SocRecCryptoError()
}

data class EncryptPrivateKeyMaterialOutput(
  val privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  val sealedPrivateKeyMaterial: XCiphertext,
)

data class EncryptDelegatedDecryptionKeyOutput(
  val trustedContactEnrollmentPakeKey: TrustedContactEnrollmentPakeKey,
  val keyConfirmation: ByteString,
  val sealedDelegatedDecryptionKey: XCiphertext,
)

data class DecryptPrivateKeyEncryptionKeyOutput(
  val trustedContactRecoveryPakeKey: TrustedContactRecoveryPakeKey,
  val keyConfirmation: ByteString,
  val sealedPrivateKeyEncryptionKey: XCiphertext,
)
