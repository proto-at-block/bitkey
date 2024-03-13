package build.wallet.recovery.socrec

import build.wallet.auth.AppAuthKeyMessageSigner
import build.wallet.bitkey.app.AppGlobalAuthPublicKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.keys.app.AppKeyImpl
import build.wallet.bitkey.socrec.DelegatedDecryptionKey
import build.wallet.bitkey.socrec.PakeCode
import build.wallet.bitkey.socrec.PrivateKeyEncryptionKey
import build.wallet.bitkey.socrec.ProtectedCustomerEnrollmentPakeKey
import build.wallet.bitkey.socrec.ProtectedCustomerIdentityKey
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TcIdentityKeyAppSignature
import build.wallet.bitkey.socrec.TrustedContactEnrollmentPakeKey
import build.wallet.bitkey.socrec.TrustedContactKeyCertificate
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.catching
import build.wallet.crypto.CurveType
import build.wallet.crypto.KeyPair
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
import build.wallet.encrypt.toXSealedData
import build.wallet.encrypt.verifyEcdsaResult
import build.wallet.serialization.hex.decodeHexWithResult
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.getOrThrow
import com.github.michaelbull.result.map
import com.github.michaelbull.result.mapError
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import com.github.michaelbull.result.coroutines.binding.binding as suspendBinding

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
    Result<ProtectedCustomerIdentityKey, SocRecCryptoError> =
    Result.catching {
      ProtectedCustomerIdentityKey(generateAsymmetricKey())
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateDelegatedDecryptionKey(): Result<DelegatedDecryptionKey, SocRecCryptoError> =
    Result.catching {
      DelegatedDecryptionKey(generateAsymmetricKey())
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerEnrollmentPakeKey(
    password: PakeCode,
  ): Result<ProtectedCustomerEnrollmentPakeKey, SocRecCryptoError> =
    // TODO: catch and return Result in Spake2 for better error handling
    // See https://linear.app/squareup/issue/BKR-1050/catch-and-return-results-in-spake2
    Result.catching {
      val keyPair = spake2.generateKeyPair(
        Spake2Params(
          Spake2Role.Alice,
          "Protected Customer",
          "Trusted Contact",
          password.bytes
        )
      )
      val appKey = AppKeyImpl(
        CurveType.Curve25519,
        keyPair.publicKey.toPublicKey(),
        keyPair.privateKey.toPrivateKey()
      )

      ProtectedCustomerEnrollmentPakeKey(appKey)
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun generateProtectedCustomerRecoveryPakeKey(
    password: PakeCode,
  ): Result<ProtectedCustomerRecoveryPakeKey, SocRecCryptoError> =
    Result.catching {
      val keyPair = spake2.generateKeyPair(
        Spake2Params(
          Spake2Role.Alice,
          "Protected Customer",
          "Trusted Contact",
          password.bytes
        )
      )
      val appKey = AppKeyImpl(
        CurveType.Curve25519,
        keyPair.publicKey.toPublicKey(),
        keyPair.privateKey.toPrivateKey()
      )

      ProtectedCustomerRecoveryPakeKey(appKey)
    }.mapError { SocRecCryptoError.KeyGenerationFailed(it) }

  override fun encryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: ProtectedCustomerEnrollmentPakeKey,
    delegatedDecryptionKey: DelegatedDecryptionKey,
  ): Result<EncryptDelegatedDecryptionKeyOutput, SocRecCryptoError> =
    Result.catching {
      // Establish PAKE secure channel
      val pakeChannelOutput = acceptPakeChannel(
        password,
        protectedCustomerEnrollmentPakeKey.publicKey,
        PAKE_ENROLLMENT_AAD.encodeUtf8()
      ).getOrThrow()
      val trustedContactEnrollmentPakeKey = TrustedContactEnrollmentPakeKey(
        pakeChannelOutput.trustedContactPakeKey
      )

      // Encrypt delegated decryption key with PAKE secure channel
      val sealedDelegatedDecryptionKey =
        xChaCha20Poly1305.encrypt(
          key = pakeChannelOutput.encryptionKey,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = delegatedDecryptionKey.publicKey.value.decodeHex(),
          aad = PAKE_ENROLLMENT_AAD.encodeUtf8()
        )

      EncryptDelegatedDecryptionKeyOutput(
        trustedContactEnrollmentPakeKey,
        pakeChannelOutput.keyConfirmation,
        sealedDelegatedDecryptionKey
      )
    }.mapError { SocRecCryptoError.EncryptionFailed(it) }

  override fun decryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: ProtectedCustomerEnrollmentPakeKey,
    encryptDelegatedDecryptionKeyOutput: EncryptDelegatedDecryptionKeyOutput,
  ): Result<DelegatedDecryptionKey, SocRecCryptoError> =
    Result.catching {
      val delegatedDecryptionKey =
        decryptSecureChannel(
          password,
          KeyPair(
            privateKey = protectedCustomerEnrollmentPakeKey.getPrivateKey(),
            publicKey = protectedCustomerEnrollmentPakeKey.publicKey,
            curveType = CurveType.Curve25519
          ),
          encryptDelegatedDecryptionKeyOutput.trustedContactEnrollmentPakeKey.key.publicKey,
          encryptDelegatedDecryptionKeyOutput.keyConfirmation,
          encryptDelegatedDecryptionKeyOutput.sealedDelegatedDecryptionKey,
          PAKE_ENROLLMENT_AAD.encodeUtf8()
        ).getOrThrow()
      DelegatedDecryptionKey(
        AppKeyImpl(
          CurveType.Curve25519,
          PublicKey(delegatedDecryptionKey.hex()),
          null
        )
      )
    }.mapError { SocRecCryptoError.DecryptionFailed(it) }

  override fun verifyKeyCertificate(
    keyCertificate: TrustedContactKeyCertificate,
    hwAuthKey: HwAuthPublicKey?,
    appGlobalAuthKey: AppGlobalAuthPublicKey?,
  ): Result<DelegatedDecryptionKey, SocRecCryptoError> {
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
      if (!isHwKeyTrusted && !isAppKeyTrusted) {
        Err(
          SocRecCryptoError.KeyCertificateVerificationFailed(
            IllegalArgumentException("None of the keys match the trusted keys provided")
          )
        ).bind<SocRecCryptoError>()
      }

      // Verify the key certificate
      if (!signatureVerifier.verifyEcdsaResult(
          signature = keyCertificate.appAuthGlobalKeyHwSignature.value,
          publicKey = hwEndorsementKey.pubKey,
          message = keyCertificate.appGlobalAuthPublicKey.pubKey.value.encodeUtf8()
        ).mapError {
          SocRecCryptoError.KeyCertificateVerificationFailed(it)
        }.bind() ||
        !signatureVerifier.verifyEcdsaResult(
          signature = keyCertificate.trustedContactIdentityKeyAppSignature.value,
          publicKey = appEndorsementKey.pubKey,
          message = (
            keyCertificate.delegatedDecryptionKey.publicKey.value.decodeHexWithResult().mapError {
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
        Err(
          SocRecCryptoError.KeyCertificateVerificationFailed(
            IllegalArgumentException("Key certificate verification failed")
          )
        ).bind<SocRecCryptoError>()
      }

      keyCertificate.delegatedDecryptionKey
    }
  }

  override suspend fun generateKeyCertificate(
    delegatedDecryptionKey: DelegatedDecryptionKey,
    hwAuthKey: HwAuthPublicKey,
    appGlobalAuthKey: AppGlobalAuthPublicKey,
    appGlobalAuthKeyHwSignature: AppGlobalAuthKeyHwSignature,
  ): Result<TrustedContactKeyCertificate, SocRecCryptoError> =
    suspendBinding {
      appAuthKeyMessageSigner.signMessage(
        appGlobalAuthKey,
        (
          delegatedDecryptionKey.publicKey.value.decodeHexWithResult().mapError {
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

  override fun <T : SocRecKey> generateAsymmetricKey(
    factory: (AppKey) -> T,
  ): Result<T, SocRecCryptoError> = Ok(factory(generateAsymmetricKey()))

  private fun generateAsymmetricKey(): AppKey {
    val (privKey, pubKey) = cryptoBox.generateKeyPair()

    return AppKeyImpl(
      CurveType.Curve25519,
      PublicKey(pubKey.bytes.hex()),
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
    delegatedDecryptionKey: DelegatedDecryptionKey,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, SocRecCryptoError> {
    return Result.catching {
      // Step 1: Encrypt the private key encryption key
      val protectedCustomerIdentityKey = generateProtectedCustomerIdentityKey().getOrThrow()
      val sealedPrivateKeyEncryptionKey = cryptoBox.encrypt(
        theirPublicKey = CryptoBoxPublicKey(delegatedDecryptionKey.publicKey.value.decodeHex()),
        myPrivateKey = CryptoBoxPrivateKey(protectedCustomerIdentityKey.getPrivateKey().bytes),
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
    protectedCustomerRecoveryPakeKey: ProtectedCustomerRecoveryPakeKey,
    delegatedDecryptionKey: DelegatedDecryptionKey,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, SocRecCryptoError> =
    Result.catching {
      // Step 1: Decrypt the private key encryption key
      val sealedPrivateKeyEncryptionKeyData = sealedPrivateKeyEncryptionKey.toXSealedData()
      if (sealedPrivateKeyEncryptionKeyData.header.version != XCIPHERTEXT_VERSION) {
        throw SocRecCryptoError.UnsupportedXCiphertextVersion
      }
      val protectedCustomerIdentityPublicKey = sealedPrivateKeyEncryptionKeyData.publicKey
        ?: throw SocRecCryptoError.PublicKeyMissing

      val privateKeyEncryptionKey = cryptoBox.decrypt(
        theirPublicKey = CryptoBoxPublicKey(protectedCustomerIdentityPublicKey.value.decodeHex()),
        myPrivateKey = CryptoBoxPrivateKey(delegatedDecryptionKey.getPrivateKey().bytes),
        sealedData = sealedPrivateKeyEncryptionKey
      )

      // Step 2: Establish PAKE secure channel
      val pakeChannelOutput = acceptPakeChannel(
        password,
        protectedCustomerRecoveryPakeKey.publicKey,
        PAKE_RECOVERY_AAD.encodeUtf8()
      ).getOrThrow()
      val trustedContactRecoveryPakeKey = TrustedContactRecoveryPakeKey(
        pakeChannelOutput.trustedContactPakeKey
      )

      // Step 3: Re-encrypt the private key encryption key with the PAKE secure channel
      val resealedPrivateKeyEncryptionKey =
        xChaCha20Poly1305.encrypt(
          key = pakeChannelOutput.encryptionKey,
          nonce = xNonceGenerator.generateXNonce(),
          plaintext = privateKeyEncryptionKey,
          aad = PAKE_RECOVERY_AAD.encodeUtf8()
        )
      DecryptPrivateKeyEncryptionKeyOutput(
        trustedContactRecoveryPakeKey,
        pakeChannelOutput.keyConfirmation,
        resealedPrivateKeyEncryptionKey
      )
    }.mapError { SocRecCryptoError.DecryptionFailed(it) }

  override fun decryptPrivateKeyMaterial(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: ProtectedCustomerRecoveryPakeKey,
    decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, SocRecCryptoError> =
    Result.catching {
      // Step 1: Decrypt the private key encryption key with the PAKE secure channel
      val privateKeyEncryptionKey = decryptSecureChannel(
        password,
        KeyPair(
          privateKey = protectedCustomerRecoveryPakeKey.getPrivateKey(),
          publicKey = protectedCustomerRecoveryPakeKey.key.publicKey,
          curveType = CurveType.Curve25519
        ),
        decryptPrivateKeyEncryptionKeyOutput.trustedContactRecoveryPakeKey.key.publicKey,
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

  private data class AcceptPakeChannelOutput(
    val trustedContactPakeKey: AppKey,
    val keyConfirmation: ByteString,
    val encryptionKey: SymmetricKey,
  )

  private fun acceptPakeChannel(
    password: PakeCode,
    protectedCustomerPakeKey: PublicKey,
    aad: ByteString,
  ): Result<AcceptPakeChannelOutput, SocRecCryptoError> =
    Result.catching {
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
        AppKeyImpl(
          CurveType.Curve25519,
          spake2KeyPair.publicKey.toPublicKey(),
          spake2KeyPair.privateKey.toPrivateKey()
        ),
        keyConfirmation,
        pakeEncryptionKey
      )
    }.mapError { SocRecCryptoError.PakeChannelAcceptanceFailed(it) }

  private fun decryptSecureChannel(
    password: PakeCode,
    protectedCustomerPakeKey: KeyPair,
    trustedContactPakeKey: PublicKey,
    keyConfirmation: ByteString,
    sealedData: XCiphertext,
    aad: ByteString,
  ): Result<ByteString, SocRecCryptoError> =
    Result.catching {
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
