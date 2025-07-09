package build.wallet.relationships

import bitkey.serialization.hex.decodeHexWithResult
import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.catchingResult
import build.wallet.crypto.*
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.encrypt.*
import build.wallet.encrypt.XSealedData.Format.WithPubkey
import build.wallet.ensure
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

@BitkeyInject(AppScope::class)
class RelationshipsCryptoImpl(
  private val symmetricKeyGenerator: SymmetricKeyGenerator,
  private val xChaCha20Poly1305: XChaCha20Poly1305,
  private val cryptoBox: CryptoBox,
  private val xNonceGenerator: XNonceGenerator,
  private val spake2: Spake2,
  private val appAuthKeyMessageSigner: AppAuthKeyMessageSigner,
  private val signatureVerifier: SignatureVerifier,
) : RelationshipsCrypto {
  companion object {
    const val PKMAT_AAD = "Bitkey Social Recovery PKMat Encryption Version 1.0"
    const val PAKE_ENROLLMENT_AAD = "Bitkey Social Recovery PAKE Enrollment Version 1.0"
    const val PAKE_RECOVERY_AAD = "Bitkey Social Recovery PAKE Recovery Version 1.0"
  }

  private fun generateProtectedCustomerIdentityKey():
    Result<AppKey<ProtectedCustomerIdentityKey>, RelationshipsCryptoError> =
    catchingResult {
      generateAsymmetricKeyUnwrapped<ProtectedCustomerIdentityKey>()
    }.mapError { RelationshipsCryptoError.KeyGenerationFailed(it) }

  override fun generateDelegatedDecryptionKey(): Result<AppKey<DelegatedDecryptionKey>, RelationshipsCryptoError> =
    catchingResult {
      generateAsymmetricKeyUnwrapped<DelegatedDecryptionKey>()
    }.mapError { RelationshipsCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerEnrollmentPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerEnrollmentPakeKey>, RelationshipsCryptoError> =
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
    }.mapError { RelationshipsCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerRecoveryPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerRecoveryPakeKey>, RelationshipsCryptoError> =
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
    }.mapError { RelationshipsCryptoError.KeyGenerationFailed(it) }

  override fun encryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
  ): Result<EncryptDelegatedDecryptionKeyOutput, RelationshipsCryptoError> =
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
    }.mapError { RelationshipsCryptoError.EncryptionFailed(it) }

  override fun decryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    encryptDelegatedDecryptionKeyOutput: EncryptDelegatedDecryptionKeyOutput,
  ): Result<PublicKey<DelegatedDecryptionKey>, RelationshipsCryptoError> =
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
    }.mapError { RelationshipsCryptoError.DecryptionFailed(it) }

  override fun verifyKeyCertificate(
    keyCertificate: TrustedContactKeyCertificate,
    hwAuthKey: HwAuthPublicKey?,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<PublicKey<DelegatedDecryptionKey>, RelationshipsCryptoError> {
    if (hwAuthKey == null && appGlobalAuthKey == null) {
      return Err(RelationshipsCryptoError.AuthKeysNotPresent)
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
        RelationshipsCryptoError.KeyCertificateVerificationFailed(
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
          RelationshipsCryptoError.KeyCertificateVerificationFailed(it)
        }.bind() &&
          signatureVerifier.verifyEcdsaResult(
            signature = keyCertificate.trustedContactIdentityKeyAppSignature.value,
            publicKey = appEndorsementKey.toSecp256k1PublicKey(),
            message = (
              keyCertificate.delegatedDecryptionKey.value.decodeHexWithResult().mapError {
                RelationshipsCryptoError.KeyCertificateVerificationFailed(it)
              }.bind().toByteArray() +
                hwEndorsementKey.pubKey.value.decodeHexWithResult().mapError {
                  RelationshipsCryptoError.KeyCertificateVerificationFailed(it)
                }.bind().toByteArray()
            ).toByteString()
          ).mapError {
            RelationshipsCryptoError.KeyCertificateVerificationFailed(it)
          }.bind()
      ) {
        RelationshipsCryptoError.KeyCertificateVerificationFailed(
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
  ): Result<TrustedContactKeyCertificate, RelationshipsCryptoError> =
    coroutineBinding {
      appAuthKeyMessageSigner.signMessage(
        appGlobalAuthKey,
        (
          delegatedDecryptionKey.value.decodeHexWithResult().mapError {
            RelationshipsCryptoError.KeyCertificateGenerationFailed(it)
          }.bind().toByteArray() +
            hwAuthKey.pubKey.value.decodeHexWithResult().mapError {
              RelationshipsCryptoError.KeyCertificateGenerationFailed(it)
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
      }.mapError { RelationshipsCryptoError.KeyCertificateGenerationFailed(it) }.bind()
    }

  override fun <T> generateAsymmetricKey(): Result<AppKey<T>, RelationshipsCryptoError> where T : SocRecKey, T : CurveType.Curve25519 =
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
  ): Result<EncryptPrivateKeyMaterialOutput, RelationshipsCryptoError> {
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
    }.mapError { RelationshipsCryptoError.EncryptionFailed(it) }
  }

  override fun encryptDescriptor(
    dek: PrivateKeyEncryptionKey,
    descriptor: ByteString,
  ): Result<XCiphertext, RelationshipsCryptoError> {
    return catchingResult {
      // Step 1: Encrypt the descriptor with the dek
      val sealedDescriptor =
        xChaCha20Poly1305.encrypt(
          key = dek,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = descriptor,
          aad = PKMAT_AAD.encodeUtf8()
        )

      sealedDescriptor
    }.mapError { RelationshipsCryptoError.EncryptionFailed(it) }
  }

  override fun encryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, RelationshipsCryptoError> {
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
        xSealedData.header.copy(format = WithPubkey, algorithm = CryptoBox.ALGORITHM),
        xSealedData.ciphertext,
        xSealedData.nonce,
        protectedCustomerIdentityKey.publicKey
      ).toOpaqueCiphertext()
    }.mapError { RelationshipsCryptoError.EncryptionFailed(it) }
  }

  override fun decryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): PrivateKeyEncryptionKey {
    val sealedPrivateKeyEncryptionKeyData = sealedPrivateKeyEncryptionKey.toXSealedData()
    if (sealedPrivateKeyEncryptionKeyData.header.format != WithPubkey) {
      throw RelationshipsCryptoError.UnsupportedXCiphertextFormat
    }
    val protectedCustomerIdentityPublicKey = sealedPrivateKeyEncryptionKeyData.publicKey
      ?: throw RelationshipsCryptoError.PublicKeyMissing

    val privateKeyEncryptionKey = cryptoBox.decrypt(
      theirPublicKey = CryptoBoxPublicKey(protectedCustomerIdentityPublicKey.value.decodeHex()),
      myPrivateKey = CryptoBoxPrivateKey(delegatedDecryptionKey.privateKey.bytes),
      sealedData = sealedPrivateKeyEncryptionKey
    )

    return PrivateKeyEncryptionKey(SymmetricKeyImpl(privateKeyEncryptionKey))
  }

  override fun transferPrivateKeyEncryptionKeyEncryption(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, RelationshipsCryptoError> =
    catchingResult {
      val privateKeyEncryptionKey = decryptPrivateKeyEncryptionKey(
        delegatedDecryptionKey = delegatedDecryptionKey,
        sealedPrivateKeyEncryptionKey = sealedPrivateKeyEncryptionKey
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
          plaintext = privateKeyEncryptionKey.raw,
          aad = PAKE_RECOVERY_AAD.encodeUtf8()
        )
      DecryptPrivateKeyEncryptionKeyOutput(
        pakeChannelOutput.trustedContactPakeKey.publicKey,
        pakeChannelOutput.keyConfirmation,
        resealedPrivateKeyEncryptionKey
      )
    }.mapError { RelationshipsCryptoError.DecryptionFailed(it) }

  override fun decryptPrivateKeyMaterial(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
    decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, RelationshipsCryptoError> =
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
    }.mapError { RelationshipsCryptoError.DecryptionFailed(it) }

  override fun decryptPrivateKeyMaterial(
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, RelationshipsCryptoError> =
    catchingResult {
      // Step 2: Decrypt the private key material with the private key encryption key
      xChaCha20Poly1305.decrypt(
        key = SymmetricKeyImpl(raw = privateKeyEncryptionKey.raw),
        ciphertextWithMetadata = sealedPrivateKeyMaterial,
        aad = PKMAT_AAD.encodeUtf8()
      )
    }.mapError { RelationshipsCryptoError.DecryptionFailed(it) }

  private data class AcceptPakeChannelOutput<T : PakeKey>(
    val trustedContactPakeKey: AppKey<T>,
    val keyConfirmation: ByteString,
    val encryptionKey: SymmetricKey,
  )

  private fun <P : ProtectedCustomerPakeKey, T : TrustedContactPakeKey> acceptPakeChannel(
    password: PakeCode,
    protectedCustomerPakeKey: PublicKey<P>,
    aad: ByteString,
  ): Result<AcceptPakeChannelOutput<T>, RelationshipsCryptoError> =
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
    }.mapError { RelationshipsCryptoError.PakeChannelAcceptanceFailed(it) }

  private fun <P : ProtectedCustomerPakeKey, T : TrustedContactPakeKey> decryptSecureChannel(
    password: PakeCode,
    protectedCustomerPakeKey: KeyPair<P>,
    trustedContactPakeKey: PublicKey<T>,
    keyConfirmation: ByteString,
    sealedData: XCiphertext,
    aad: ByteString,
  ): Result<ByteString, RelationshipsCryptoError> =
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
    }.mapError { RelationshipsCryptoError.PakeChannelDecryptionFailed(it) }
}
