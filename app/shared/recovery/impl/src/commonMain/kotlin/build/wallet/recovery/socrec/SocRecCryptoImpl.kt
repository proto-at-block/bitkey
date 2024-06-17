package build.wallet.recovery.socrec

import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.PrivateKeyEncryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.socrec.ProtectedCustomerPakeKey
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TcIdentityKeyAppSignature
import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.bitkey.socrec.TrustedContactKeyCertificate
import build.wallet.bitkey.socrec.TrustedContactPakeKey
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.catchingResult
import build.wallet.crypto.CurveType
import build.wallet.crypto.KeyPair
import build.wallet.crypto.PakeKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.crypto.Spake2
import build.wallet.crypto.Spake2KeyPair
import build.wallet.crypto.Spake2Params
import build.wallet.crypto.Spake2Role
import build.wallet.crypto.SymmetricKey
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.crypto.toPrivateKey
import build.wallet.crypto.toPublicKey
import build.wallet.crypto.toSpake2PrivateKey
import build.wallet.crypto.toSpake2PublicKey
import build.wallet.encrypt.CryptoBox
import build.wallet.encrypt.CryptoBoxPrivateKey
import build.wallet.encrypt.CryptoBoxPublicKey
import build.wallet.encrypt.SignatureVerifier
import build.wallet.encrypt.SymmetricKeyGenerator
import build.wallet.encrypt.XChaCha20Poly1305
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XNonceGenerator
import build.wallet.encrypt.XSealedData
import build.wallet.encrypt.toSecp256k1PublicKey
import build.wallet.encrypt.toXSealedData
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.ensure
import build.wallet.serialization.hex.decodeHexWithResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

class SocRecCryptoImpl(
  private val symmetricKeyGenerator: SymmetricKeyGenerator,
  private val xChaCha20Poly1305: XChaCha20Poly1305,
  private val cryptoBox: CryptoBox,
  private val xNonceGenerator: XNonceGenerator,
  private val spake2: Spake2,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val signatureVerifier: SignatureVerifier,
) : SocRecCrypto {
  companion object {
    const val PKMAT_AAD = "Bitkey Social Recovery PKMat Encryption Version 1.0"
    const val PAKE_ENROLLMENT_AAD = "Bitkey Social Recovery PAKE Enrollment Version 1.0"
    const val PAKE_RECOVERY_AAD = "Bitkey Social Recovery PAKE Recovery Version 1.0"
    const val XCIPHERTEXT_VERSION = 2
  }

  private fun generateProtectedCustomerIdentityKey():
    Result<AppKey<ProtectedCustomerIdentityKey>, SocRecCryptoError> =
    catchingResult {
      generateAsymmetricKeyUnwrapped<ProtectedCustomerIdentityKey>()
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateDelegatedDecryptionKey(): Result<AppKey<DelegatedDecryptionKey>, SocRecCryptoError> =
    catchingResult {
      generateAsymmetricKeyUnwrapped<DelegatedDecryptionKey>()
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerEnrollmentPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerEnrollmentPakeKey>, SocRecCryptoError> =
    // TODO: catch and return Result in Spake2 for better error handling
    // See https://linear.app/squareup/issue/BKR-1050/catch-and-return-results-in-spake2
    catchingResult {
      val keyPair = spake2.generateKeyPair(
        Spake2Params(
          Spake2Role.Alice,
          "Protected Customer",
          "Trusted Contact",
          password.bytes
        )
      )
      AppKey<ProtectedCustomerEnrollmentPakeKey>(
        keyPair.publicKey.toPublicKey(),
        keyPair.privateKey.toPrivateKey()
      )
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerRecoveryPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerRecoveryPakeKey>, SocRecCryptoError> =
    catchingResult {
      val keyPair = spake2.generateKeyPair(
        Spake2Params(
          Spake2Role.Alice,
          "Protected Customer",
          "Trusted Contact",
          password.bytes
        )
      )
      AppKey<ProtectedCustomerRecoveryPakeKey>(
        keyPair.publicKey.toPublicKey(),
        keyPair.privateKey.toPrivateKey()
      )
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun encryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
  ): Result<EncryptDelegatedDecryptionKeyOutput, SocRecCryptoError> =
    catchingResult {
      // Establish PAKE secure channel
      val pakeChannelOutput =
        acceptPakeChannel<ProtectedCustomerEnrollmentPakeKey, TrustedContactEnrollmentPakeKey>(
          password,
          protectedCustomerEnrollmentPakeKey,
          PAKE_ENROLLMENT_AAD.encodeUtf8()
        ).getOrThrow()

      // Encrypt delegated decryption key with PAKE secure channel
      val sealedDelegatedDecryptionKey =
        xChaCha20Poly1305.encrypt(
          key = pakeChannelOutput.encryptionKey,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = delegatedDecryptionKey.value.decodeHex(),
          aad = PAKE_ENROLLMENT_AAD.encodeUtf8()
        )

      EncryptDelegatedDecryptionKeyOutput(
        pakeChannelOutput.trustedContactPakeKey.publicKey,
        pakeChannelOutput.keyConfirmation,
        sealedDelegatedDecryptionKey
      )
    }.mapError { SocRecCryptoError.EncryptionFailed(it) }

  override fun decryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    encryptDelegatedDecryptionKeyOutput: EncryptDelegatedDecryptionKeyOutput,
  ): Result<PublicKey<DelegatedDecryptionKey>, SocRecCryptoError> =
    catchingResult {
      val delegatedDecryptionKey =
        decryptSecureChannel(
          password,
          protectedCustomerEnrollmentPakeKey,
          encryptDelegatedDecryptionKeyOutput.trustedContactEnrollmentPakeKey,
          encryptDelegatedDecryptionKeyOutput.keyConfirmation,
          encryptDelegatedDecryptionKeyOutput.sealedDelegatedDecryptionKey,
          PAKE_ENROLLMENT_AAD.encodeUtf8()
        ).getOrThrow()
      PublicKey<DelegatedDecryptionKey>(delegatedDecryptionKey.hex())
    }.mapError { SocRecCryptoError.DecryptionFailed(it) }

  override fun verifyKeyCertificate(
    keyCertificate: TrustedContactKeyCertificate,
    hwAuthKey: HwAuthPublicKey?,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<PublicKey<DelegatedDecryptionKey>, SocRecCryptoError> {
    if (hwAuthKey == null && appGlobalAuthKey == null) {
      return Err(SocRecCryptoError.AuthKeysNotPresent)
    }

    return binding {
      // Check for mismatch between the trusted keys and the key certificate
      val hwEndorsementKey = keyCertificate.hwAuthPublicKey
      val appEndorsementKey = keyCertificate.appGlobalAuthPublicKey

      // Check if the hwEndorsementKey matches the trusted key
      val isHwKeyTrusted = hwEndorsementKey == hwAuthKey
      // Check if the appEndorsementKey matches the trusted key
      val isAppKeyTrusted = appEndorsementKey == appGlobalAuthKey

      // Ensure at least one key matches a trusted key
      ensure(isHwKeyTrusted || isAppKeyTrusted) {
        SocRecCryptoError.KeyCertificateVerificationFailed(
          IllegalArgumentException("None of the keys match the trusted keys provided")
        )
      }

      // Verify the key certificate
      ensure(
        signatureVerifier.verifyEcdsaResult(
          signature = keyCertificate.appAuthGlobalKeyHwSignature.value,
          publicKey = hwEndorsementKey.pubKey,
          message = keyCertificate.appGlobalAuthPublicKey.value.encodeUtf8()
        ).mapError {
          SocRecCryptoError.KeyCertificateVerificationFailed(it)
        }.bind() &&
          signatureVerifier.verifyEcdsaResult(
            signature = keyCertificate.trustedContactIdentityKeyAppSignature.value,
            publicKey = appEndorsementKey.toSecp256k1PublicKey(),
            message = (
              keyCertificate.delegatedDecryptionKey.value.decodeHexWithResult().mapError {
                SocRecCryptoError.KeyCertificateVerificationFailed(it)
              }.bind().toByteArray() +
                hwEndorsementKey.pubKey.value.decodeHexWithResult().mapError {
                  SocRecCryptoError.KeyCertificateVerificationFailed(it)
                }.bind().toByteArray()
            ).toByteString()
          ).mapError {
            SocRecCryptoError.KeyCertificateVerificationFailed(it)
          }.bind()
      ) {
        SocRecCryptoError.KeyCertificateVerificationFailed(
          IllegalArgumentException("Key certificate verification failed")
        )
      }

      keyCertificate.delegatedDecryptionKey
    }
  }

  override suspend fun generateKeyCertificate(
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    hwAuthKey: HwAuthPublicKey,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<TrustedContactKeyCertificate, SocRecCryptoError> =
    coroutineBinding {
      appAuthKeyMessageSigner.signMessage(
        appGlobalAuthKey,
        (
          delegatedDecryptionKey.value.decodeHexWithResult().mapError {
            SocRecCryptoError.KeyCertificateGenerationFailed(it)
          }.bind().toByteArray() +
            hwAuthKey.pubKey.value.decodeHexWithResult().mapError {
              SocRecCryptoError.KeyCertificateGenerationFailed(it)
            }.bind().toByteArray()
        ).toByteString()
      ).map {
        TrustedContactKeyCertificate(
          delegatedDecryptionKey,
          hwAuthKey,
          appGlobalAuthKey,
          appGlobalAuthKeyHwSignature,
          TcIdentityKeyAppSignature(it)
        )
      }.mapError { SocRecCryptoError.KeyCertificateGenerationFailed(it) }.bind()
    }

  override fun <T> generateAsymmetricKey(): Result<AppKey<T>, SocRecCryptoError> where T : SocRecKey, T : CurveType.Curve25519 =
    Ok(generateAsymmetricKeyUnwrapped())

  private fun <T> generateAsymmetricKeyUnwrapped(): AppKey<T> where T : SocRecKey, T : CurveType.Curve25519 {
    val (privKey, pubKey) = cryptoBox.generateKeyPair()

    return AppKey(
      PublicKey(pubKey.bytes.hex()),
      PrivateKey(privKey.bytes)
    )
  }

  override fun encryptPrivateKeyMaterial(
    privateKeyMaterial: ByteString,
  ): Result<EncryptPrivateKeyMaterialOutput, SocRecCryptoError> {
    return catchingResult {
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

  override fun encryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, SocRecCryptoError> {
    return catchingResult {
      // Step 1: Encrypt the private key encryption key
      val protectedCustomerIdentityKey = generateProtectedCustomerIdentityKey().getOrThrow()
      val sealedPrivateKeyEncryptionKey = cryptoBox.encrypt(
        theirPublicKey = CryptoBoxPublicKey(delegatedDecryptionKey.value.decodeHex()),
        myPrivateKey = CryptoBoxPrivateKey(protectedCustomerIdentityKey.privateKey.bytes),
        nonce = xNonceGenerator.generateXNonce(),
        plaintext = privateKeyEncryptionKey.raw
      )

      // Step 2: Embed the public key in the ciphertext
      val xSealedData = sealedPrivateKeyEncryptionKey.toXSealedData()
      XSealedData(
        xSealedData.header.copy(version = 2, algorithm = CryptoBox.ALGORITHM),
        xSealedData.ciphertext,
        xSealedData.nonce,
        protectedCustomerIdentityKey.publicKey
      ).toOpaqueCiphertext()
    }.mapError { SocRecCryptoError.EncryptionFailed(it) }
  }

  override fun decryptPrivateKeyEncryptionKey(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, SocRecCryptoError> =
    catchingResult {
      // Step 1: Decrypt the private key encryption key
      val sealedPrivateKeyEncryptionKeyData = sealedPrivateKeyEncryptionKey.toXSealedData()
      if (sealedPrivateKeyEncryptionKeyData.header.version != XCIPHERTEXT_VERSION) {
        throw SocRecCryptoError.UnsupportedXCiphertextVersion
      }
      val protectedCustomerIdentityPublicKey = sealedPrivateKeyEncryptionKeyData.publicKey
        ?: throw SocRecCryptoError.PublicKeyMissing

      val privateKeyEncryptionKey = cryptoBox.decrypt(
        theirPublicKey = CryptoBoxPublicKey(protectedCustomerIdentityPublicKey.value.decodeHex()),
        myPrivateKey = CryptoBoxPrivateKey(delegatedDecryptionKey.privateKey.bytes),
        sealedData = sealedPrivateKeyEncryptionKey
      )

      // Step 2: Establish PAKE secure channel
      val pakeChannelOutput =
        acceptPakeChannel<ProtectedCustomerRecoveryPakeKey, TrustedContactRecoveryPakeKey>(
          password,
          protectedCustomerRecoveryPakeKey,
          PAKE_RECOVERY_AAD.encodeUtf8()
        ).getOrThrow()

      // Step 3: Re-encrypt the private key encryption key with the PAKE secure channel
      val resealedPrivateKeyEncryptionKey =
        xChaCha20Poly1305.encrypt(
          key = pakeChannelOutput.encryptionKey,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = privateKeyEncryptionKey,
          aad = PAKE_RECOVERY_AAD.encodeUtf8()
        )
      DecryptPrivateKeyEncryptionKeyOutput(
        pakeChannelOutput.trustedContactPakeKey.publicKey,
        pakeChannelOutput.keyConfirmation,
        resealedPrivateKeyEncryptionKey
      )
    }.mapError { SocRecCryptoError.DecryptionFailed(it) }

  override fun decryptPrivateKeyMaterial(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
    decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, SocRecCryptoError> =
    catchingResult {
      // Step 1: Decrypt the private key encryption key with the PAKE secure channel
      val privateKeyEncryptionKey = decryptSecureChannel(
        password,
        protectedCustomerRecoveryPakeKey,
        decryptPrivateKeyEncryptionKeyOutput.trustedContactRecoveryPakeKey,
        decryptPrivateKeyEncryptionKeyOutput.keyConfirmation,
        decryptPrivateKeyEncryptionKeyOutput.sealedPrivateKeyEncryptionKey,
        PAKE_RECOVERY_AAD.encodeUtf8()
      ).getOrThrow()

      // Step 2: Decrypt the private key material with the private key encryption key
      xChaCha20Poly1305.decrypt(
        key = SymmetricKeyImpl(raw = privateKeyEncryptionKey),
        ciphertextWithMetadata = sealedPrivateKeyMaterial,
        aad = PKMAT_AAD.encodeUtf8()
      )
    }.mapError { SocRecCryptoError.DecryptionFailed(it) }

  private data class AcceptPakeChannelOutput<T : PakeKey>(
    val trustedContactPakeKey: AppKey<T>,
    val keyConfirmation: ByteString,
    val encryptionKey: SymmetricKey,
  )

  private fun <P : ProtectedCustomerPakeKey, T : TrustedContactPakeKey> acceptPakeChannel(
    password: PakeCode,
    protectedCustomerPakeKey: PublicKey<P>,
    aad: ByteString,
  ): Result<AcceptPakeChannelOutput<T>, SocRecCryptoError> =
    catchingResult {
      val spake2Params = Spake2Params(
        Spake2Role.Bob,
        "Trusted Contact",
        "Protected Customer",
        password.bytes
      )
      val spake2KeyPair = spake2.generateKeyPair(spake2Params)
      val pakeSymmetricKeys = spake2.processTheirPublicKey(
        spake2Params,
        spake2KeyPair,
        protectedCustomerPakeKey.toSpake2PublicKey(),
        aad
      )
      val keyConfirmation = spake2.generateKeyConfMsg(spake2Params.role, pakeSymmetricKeys)
      val pakeEncryptionKey = SymmetricKeyImpl(raw = pakeSymmetricKeys.bobEncryptionKey)
      AcceptPakeChannelOutput(
        AppKey<T>(
          spake2KeyPair.publicKey.toPublicKey(),
          spake2KeyPair.privateKey.toPrivateKey()
        ),
        keyConfirmation,
        pakeEncryptionKey
      )
    }.mapError { SocRecCryptoError.PakeChannelAcceptanceFailed(it) }

  private fun <P : ProtectedCustomerPakeKey, T : TrustedContactPakeKey> decryptSecureChannel(
    password: PakeCode,
    protectedCustomerPakeKey: KeyPair<P>,
    trustedContactPakeKey: PublicKey<T>,
    keyConfirmation: ByteString,
    sealedData: XCiphertext,
    aad: ByteString,
  ): Result<ByteString, SocRecCryptoError> =
    catchingResult {
      val spake2Params = Spake2Params(
        Spake2Role.Alice,
        "Protected Customer",
        "Trusted Contact",
        password.bytes
      )
      val pakeSymmetricKeys = spake2.processTheirPublicKey(
        spake2Params,
        Spake2KeyPair(
          protectedCustomerPakeKey.privateKey.toSpake2PrivateKey(),
          protectedCustomerPakeKey.publicKey.toSpake2PublicKey()
        ),
        trustedContactPakeKey.toSpake2PublicKey(),
        aad
      )
      spake2.processKeyConfMsg(
        spake2Params.role,
        keyConfirmation,
        pakeSymmetricKeys
      )
      val pakeEncryptionKey = SymmetricKeyImpl(raw = pakeSymmetricKeys.bobEncryptionKey)

      xChaCha20Poly1305.decrypt(
        key = pakeEncryptionKey,
        ciphertextWithMetadata = sealedData,
        aad = aad
      )
    }.mapError { SocRecCryptoError.PakeChannelDecryptionFailed(it) }
}
