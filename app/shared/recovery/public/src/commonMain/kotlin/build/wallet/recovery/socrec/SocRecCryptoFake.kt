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
import build.wallet.bitkey.socrec.TrustedContactEnrollmentKey
import build.wallet.bitkey.socrec.TrustedContactIdentityKey
import build.wallet.bitkey.socrec.TrustedContactRecoveryKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.Secp256k1PrivateKey
import build.wallet.encrypt.Secp256k1PublicKey
import build.wallet.encrypt.XCiphertext
import build.wallet.encrypt.XNonce
import build.wallet.encrypt.XSealedData
import build.wallet.encrypt.toXSealedData
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.ionspin.kotlin.bignum.integer.BigInteger
import com.ionspin.kotlin.bignum.integer.toBigInteger
import com.ionspin.kotlin.bignum.modular.ModularBigInteger
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString
import kotlin.random.Random

/**
 * This implementation uses simplified crypto operations to simulate the real
 * crypto operations. The crypto operations are not secure and should not be
 * used in production. IT IS EXTREMELY DANGEROUS AND RECKLESS TO USE THIS IN
 * PRODUCTION!
 *
 * The default randomness generator has a fixed seed, ensuring that key
 * generation is deterministic in tests.
 */
class SocRecCryptoFake(private val random: Random = Random(0)) : SocRecCrypto {
  private val g = Secp256k1.g()
  private val q = ModularBigInteger.creatorForModulo(Secp256k1.Q)

  /** Generates a usable but insecure key pair */
  override fun generateProtectedCustomerIdentityKey():
    Result<ProtectedCustomerIdentityKey, SocRecCryptoError> =
    Ok(ProtectedCustomerIdentityKey(generateAsymmetricKey()))

  override fun generateProtectedCustomerEphemeralKey():
    Result<ProtectedCustomerEphemeralKey, SocRecCryptoError> =
    Ok(ProtectedCustomerEphemeralKey(generateAsymmetricKey()))

  /** Generates a usable but insecure key pair */
  override fun generateTrustedContactIdentityKey():
    Result<TrustedContactIdentityKey, SocRecCryptoError> =
    Ok(TrustedContactIdentityKey(generateAsymmetricKey()))

  override fun generateProtectedCustomerEnrollmentKey(
    password: ByteString,
  ): Result<ProtectedCustomerEnrollmentKey, SocRecCryptoError> {
    return Ok(
      ProtectedCustomerEnrollmentKey(
        generatePakeKey(password)
      )
    )
  }

  override fun generateProtectedCustomerRecoveryKey(
    password: ByteString,
  ): Result<ProtectedCustomerRecoveryKey, SocRecCryptoError> {
    return Ok(
      ProtectedCustomerRecoveryKey(
        generatePakeKey(password)
      )
    )
  }

  private fun generatePakeKey(password: ByteString): AppKey {
    // x ⭠ ℤ_q
    val privKey = randomBytes()
    val x = q.parseString(privKey.hex(), 16)
    // 'X = xG
    val basePubKey = g * x
    // X = 'X * H(password)
    val pubKey = basePubKey * q.parseString(password.sha256().hex(), 16)

    return AppKeyImpl(
      CurveType.SECP256K1,
      PublicKey(pubKey.secSerialize().hex()),
      PrivateKey(x.toByteArray().toByteString())
    )
  }

  override fun encryptTrustedContactIdentityKey(
    password: ByteString,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<EncryptTrustedContactIdentityKeyOutput, SocRecCryptoError> {
    val trustedContactIdentityKeyBytes = trustedContactIdentityKey.publicKey.value.decodeHex()
    // Generate PAKE keys
    val secureChannelOutput =
      establishSecureChannel(
        password,
        protectedCustomerEnrollmentKey.publicKey,
        trustedContactIdentityKeyBytes.size
      )
    // Encrypt TC Identity Key
    val trustedContactIdentityKeyCiphertext =
      trustedContactIdentityKeyBytes.xorWith(secureChannelOutput.sharedSecretKey)

    return Ok(
      EncryptTrustedContactIdentityKeyOutput(
        sealedTrustedContactIdentityKey =
          XSealedData(
            XSealedData.Header(algorithm = "SocRecCryptoFake"),
            ciphertext = trustedContactIdentityKeyCiphertext,
            nonce = XNonce(ByteString.EMPTY)
          ).toOpaqueCiphertext(),
        trustedContactEnrollmentKey =
          TrustedContactEnrollmentKey(
            secureChannelOutput.trustedContactPasswordAuthenticatedKey
          ),
        keyConfirmation = secureChannelOutput.keyConfirmation
      )
    )
  }

  override fun decryptTrustedContactIdentityKey(
    password: ByteString,
    protectedCustomerEnrollmentKey: ProtectedCustomerEnrollmentKey,
    encryptTrustedContactIdentityKeyOutput: EncryptTrustedContactIdentityKeyOutput,
  ): Result<TrustedContactIdentityKey, SocRecCryptoError> =
    binding {
      val trustedContactIdentityKey =
        decryptSecureChannel(
          password,
          (protectedCustomerEnrollmentKey.key as AppKeyImpl).privateKey!!,
          encryptTrustedContactIdentityKeyOutput.trustedContactEnrollmentKey.publicKey,
          encryptTrustedContactIdentityKeyOutput.keyConfirmation,
          encryptTrustedContactIdentityKeyOutput.sealedTrustedContactIdentityKey
        ).bind()

      TrustedContactIdentityKey(
        AppKeyImpl(
          CurveType.SECP256K1,
          PublicKey(trustedContactIdentityKey.hex()),
          null
        )
      )
    }

  override fun verifyKeyCertificate(
    keyCertificate: KeyCertificate,
    trustedHwEndorsementKey: HwAuthPublicKey?,
    trustedAppEndorsementKey: AppGlobalAuthPublicKey?,
  ): Result<TrustedContactIdentityKey, SocRecCryptoError> {
    val hwEndorsementKey = keyCertificate.hwEndorsementKey
    val appEndorsementKey = keyCertificate.appEndorsementKey
    // Check for presence of at least one trusted key parameter
    if (trustedHwEndorsementKey == null && trustedAppEndorsementKey == null) {
      return Err(
        SocRecCryptoError.KeyCertificateVerificationFailed(
          IllegalArgumentException("No trusted key provided")
        )
      )
    }
    // Check for mismatch between the trusted keys and the key certificate
    if (trustedHwEndorsementKey != null && hwEndorsementKey != trustedHwEndorsementKey.pubKey) {
      return Err(
        SocRecCryptoError.KeyCertificateVerificationFailed(
          IllegalArgumentException("HwEndorsementKey does not match the trusted key")
        )
      )
    }
    if (trustedAppEndorsementKey != null && appEndorsementKey != trustedAppEndorsementKey.pubKey) {
      return Err(
        SocRecCryptoError.KeyCertificateVerificationFailed(
          IllegalArgumentException("AppEndorsementKey does not match the trusted key")
        )
      )
    }
    // Check if the hwEndorsementKey matches the trusted key
    val isHwKeyTrusted = hwEndorsementKey == trustedHwEndorsementKey?.pubKey
    // Check if the appEndorsementKey matches the trusted key
    val isAppKeyTrusted = appEndorsementKey == trustedAppEndorsementKey?.pubKey

    // Ensure at least one key matches a trusted key
    if (isHwKeyTrusted || isAppKeyTrusted) {
      if (verifySig(
          keyCertificate.hwSignature,
          hwEndorsementKey,
          appEndorsementKey.value.decodeHex()
        ) &&
        verifySig(
          keyCertificate.appSignature,
          appEndorsementKey,
          keyCertificate.trustedContactIdentityKey.publicKey.value.decodeHex()
        )
      ) {
        return Ok(keyCertificate.trustedContactIdentityKey)
      } else {
        return Err(
          SocRecCryptoError.KeyCertificateVerificationFailed(
            IllegalArgumentException("Key certificate verification failed")
          )
        )
      }
    } else {
      return Err(
        SocRecCryptoError.KeyCertificateVerificationFailed(
          IllegalArgumentException("None of the keys match the trusted keys provided")
        )
      )
    }
  }

  override fun generateKeyCertificate(
    trustedContactIdentityKey: TrustedContactIdentityKey,
    hwEndorsementKey: HwAuthPublicKey,
    appEndorsementKey: AppGlobalAuthKeypair,
    hwSignature: ByteString,
  ): Result<KeyCertificate, SocRecCryptoError> {
    val appSignature =
      sign(
        appEndorsementKey.privateKey.key,
        appEndorsementKey.publicKey.pubKey,
        trustedContactIdentityKey.publicKey.value.decodeHex()
      )
    return Ok(
      KeyCertificate(
        trustedContactIdentityKey = trustedContactIdentityKey,
        hwEndorsementKey = hwEndorsementKey.pubKey,
        appEndorsementKey = appEndorsementKey.publicKey.pubKey,
        hwSignature = hwSignature,
        appSignature = appSignature
      )
    )
  }

  override fun <T : SocRecKey> generateAsymmetricKey(
    factory: (AppKey) -> T,
  ): Result<T, SocRecCryptoError> = Ok(factory(generateAsymmetricKey()))

  private fun generateAsymmetricKey(): AppKey {
    val (privKey, pubKey) = generateKeyPair()

    return AppKeyImpl(
      CurveType.SECP256K1,
      PublicKey(pubKey.value),
      PrivateKey(privKey.bytes)
    )
  }

  /**
   * Generates a ciphertext and 256-bit key with an insecure RNG and an insecure
   * encryption algorithm (i.e. naive key expansion and XOR).
   */
  override fun encryptPrivateKeyMaterial(
    privateKeyMaterial: ByteString,
  ): Result<EncryptPrivateKeyMaterialOutput, SocRecCryptoError> {
    val privateKeyEncryptionKey = randomBytes()
    val expandedKey = expandKey(privateKeyEncryptionKey, privateKeyMaterial.size)
    val privateKeyMaterialCiphertext = privateKeyMaterial.xorWith(expandedKey)
    return Ok(
      EncryptPrivateKeyMaterialOutput(
        sealedPrivateKeyMaterial =
          XSealedData(
            XSealedData.Header(algorithm = "SocRecCryptoFake"),
            ciphertext = privateKeyMaterialCiphertext,
            nonce = XNonce(ByteString.EMPTY)
          ).toOpaqueCiphertext(),
        privateKeyEncryptionKey =
          PrivateKeyEncryptionKey(
            SymmetricKeyFake(privateKeyEncryptionKey)
          )
      )
    )
  }

  /**
   * Generates a ciphertext with an insecure Diffie-Hellman derivation and an
   * insecure encryption algorithm (i.e. naive key expansion and XOR).
   */
  override fun encryptPrivateKeyEncryptionKey(
    trustedContactIdentityKey: TrustedContactIdentityKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, SocRecCryptoError> {
    require(privateKeyEncryptionKey.key is SymmetricKeyFake)
    val keyMat = (privateKeyEncryptionKey.key as SymmetricKeyFake).raw
    val deserializedPubKey =
      Point.secDeserialize(
        trustedContactIdentityKey.publicKey.value.decodeHex()
      )
    val deserializedPrivKey =
      q.parseString(
        (protectedCustomerIdentityKey.key as AppKeyImpl).privateKey!!.bytes.hex(),
        16
      )
    val sharedSecret = deserializedPubKey * deserializedPrivKey

    val expandedKey =
      expandKey(
        sharedSecret.secSerialize(),
        privateKeyEncryptionKey.length
      )
    val privateKeyEncryptionKeyCiphertext = keyMat.xorWith(expandedKey)

    return Ok(
      XSealedData(
        header = XSealedData.Header(algorithm = "SocRecCryptoFake"),
        ciphertext = privateKeyEncryptionKeyCiphertext,
        nonce = XNonce(ByteString.EMPTY)
      ).toOpaqueCiphertext()
    )
  }

  /**
   * Derives a shared secret and encrypts it with an insecure Diffie-Hellman
   * derivation and an insecure encryption algorithm (i.e. naive key expansion
   * and XOR).
   */
  override fun deriveAndEncryptSharedSecret(
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
  ): Result<XCiphertext, SocRecCryptoError> {
    val deserializedIdentityPubKey =
      Point.secDeserialize(
        protectedCustomerIdentityKey.publicKey.value.decodeHex()
      )
    val deserializedIdentityPrivKey =
      q.parseString(
        (trustedContactIdentityKey.key as AppKeyImpl).privateKey!!.bytes.hex(),
        16
      )
    val identitySharedSecret = deserializedIdentityPubKey * deserializedIdentityPrivKey
    val identitySharedSecretKey =
      expandKey(
        // Expand the shared secret to the size of the private key encryption key
        identitySharedSecret.secSerialize(),
        32
      )
    val deserializedEphemeralPubKey =
      Point.secDeserialize(
        protectedCustomerEphemeralKey.publicKey.value.decodeHex()
      )
    val ephemeralSharedSecret = deserializedEphemeralPubKey * deserializedIdentityPrivKey
    val ephemeralSharedSecretKey =
      expandKey(
        ephemeralSharedSecret.secSerialize(),
        identitySharedSecretKey.size
      )
    val sharedSecretKeyCipherText =
      identitySharedSecretKey.xorWith(ephemeralSharedSecretKey)

    return Ok(
      XSealedData(
        header = XSealedData.Header(algorithm = "SocRecCryptoFake"),
        ciphertext = sharedSecretKeyCipherText,
        nonce = XNonce(ByteString.EMPTY)
      ).toOpaqueCiphertext()
    )
  }

  override fun decryptPrivateKeyEncryptionKey(
    password: ByteString,
    protectedCustomerRecoveryKey: ProtectedCustomerRecoveryKey,
    protectedCustomerIdentityKey: ProtectedCustomerIdentityKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, SocRecCryptoError> {
    // Generate PAKE keys
    val secureChannelOutput =
      establishSecureChannel(
        password,
        protectedCustomerRecoveryKey.publicKey,
        sealedPrivateKeyEncryptionKey.toXSealedData().ciphertext.size
      )
    val deserializedIdentityPubKey =
      Point.secDeserialize(
        protectedCustomerIdentityKey.publicKey.value.decodeHex()
      )
    val deserializedIdentityPrivKey =
      q.parseString(
        (trustedContactIdentityKey.key as AppKeyImpl).privateKey!!.bytes.hex(),
        16
      )
    val identitySharedSecret = deserializedIdentityPubKey * deserializedIdentityPrivKey
    val identitySharedSecretKey =
      expandKey(
        // Expand the shared secret to the size of the private key encryption key
        identitySharedSecret.secSerialize(),
        32
      )
    // Decrypt PKEK
    val privateKeyEncryptionKey =
      sealedPrivateKeyEncryptionKey.toXSealedData().ciphertext.xorWith(identitySharedSecretKey)
    // Encrypt PKEK with PAKE shared secret
    val pakeSealedPrivateKeyEncryptionKey =
      privateKeyEncryptionKey.xorWith(
        secureChannelOutput.sharedSecretKey
      )

    return Ok(
      DecryptPrivateKeyEncryptionKeyOutput(
        trustedContactRecoveryKey =
          TrustedContactRecoveryKey(
            secureChannelOutput.trustedContactPasswordAuthenticatedKey
          ),
        keyConfirmation = secureChannelOutput.keyConfirmation,
        sealedPrivateKeyEncryptionKey =
          XSealedData(
            XSealedData.Header(algorithm = "SocRecCryptoFake"),
            ciphertext = pakeSealedPrivateKeyEncryptionKey,
            nonce = XNonce(ByteString.EMPTY)
          ).toOpaqueCiphertext()
      )
    )
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
          secureChannelData.protectedCustomerEphemeralKey,
          trustedContactIdentityKey,
          secureChannelData.sealedPrivateKeyEncryptionKey,
          sealedPrivateKeyMaterial
        )
      }
      is DecryptPrivateKeyMaterialParams.V2 -> {
        decryptPrivateKeyMaterialV2(
          secureChannelData.password,
          secureChannelData.protectedCustomerRecoveryKey,
          secureChannelData.decryptPrivateKeyEncryptionKeyOutput,
          sealedPrivateKeyMaterial
        )
      }
    }
  }

  /**
   * Decrypts the shared secret, private key encryption key, and private key
   * material with an insecure Diffie-Hellman derivation and an insecure
   * decryption algorithm (i.e. naive key expansion and XOR).
   */
  private fun decryptPrivateKeyMaterialV1(
    sharedSecretCipherText: XCiphertext,
    protectedCustomerEphemeralKey: ProtectedCustomerEphemeralKey,
    trustedContactIdentityKey: TrustedContactIdentityKey,
    sealedPrivateKeyEncryptionKey: XCiphertext,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, SocRecCryptoError> {
    val sharedSecretSealedData = sharedSecretCipherText.toXSealedData()
    val sealedPrivateKeyEncryptionKeyData = sealedPrivateKeyEncryptionKey.toXSealedData()
    val sealedPrivateKeyMaterialData = sealedPrivateKeyMaterial.toXSealedData()

    val deserializedIdentityPubKey =
      Point.secDeserialize(
        trustedContactIdentityKey.publicKey.value.decodeHex()
      )
    val deserializedEphemeralPrivKey =
      q.parseString(
        (protectedCustomerEphemeralKey.key as AppKeyImpl).privateKey!!.bytes.hex(),
        16
      )
    val ephemeralSharedSecret = deserializedIdentityPubKey * deserializedEphemeralPrivKey
    val ephemeralSharedSecretKey =
      expandKey(
        ephemeralSharedSecret.secSerialize(),
        sharedSecretSealedData.ciphertext.size
      )
    val sharedSecretKey = sharedSecretSealedData.ciphertext.xorWith(ephemeralSharedSecretKey)
    val privateKeyEncryptionKey =
      sealedPrivateKeyEncryptionKeyData.ciphertext.xorWith(
        sharedSecretKey
      )
    val expandedKey =
      expandKey(privateKeyEncryptionKey, sealedPrivateKeyMaterialData.ciphertext.size)

    return Ok(sealedPrivateKeyMaterialData.ciphertext.xorWith(expandedKey))
  }

  private fun decryptPrivateKeyMaterialV2(
    password: ByteString,
    protectedCustomerRecoveryKey: ProtectedCustomerRecoveryKey,
    decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, SocRecCryptoError> =
    // Decrypt PKEK
    binding {
      val privateKeyEncryptionKey =
        decryptSecureChannel(
          password,
          (protectedCustomerRecoveryKey.key as AppKeyImpl).privateKey!!,
          decryptPrivateKeyEncryptionKeyOutput.trustedContactRecoveryKey.publicKey,
          decryptPrivateKeyEncryptionKeyOutput.keyConfirmation,
          decryptPrivateKeyEncryptionKeyOutput.sealedPrivateKeyEncryptionKey
        ).bind()
      // Decrypt PKMat
      val expandedPrivateKeyEncryptionKey =
        expandKey(privateKeyEncryptionKey, sealedPrivateKeyMaterial.toXSealedData().ciphertext.size)

      sealedPrivateKeyMaterial.toXSealedData().ciphertext.xorWith(expandedPrivateKeyEncryptionKey)
    }

  private data class EstablishSecureChannelOutput(
    val trustedContactPasswordAuthenticatedKey: AppKey,
    val keyConfirmation: ByteString,
    val sharedSecretKey: ByteString,
  )

  private fun establishSecureChannel(
    password: ByteString,
    protectedCustomerPasswordAuthenticatedKey: PublicKey,
    length: Int,
  ): EstablishSecureChannelOutput {
    // Generate TC PAKE Key
    // x ⭠ ℤ_q
    val privKey = randomBytes()
    val x = q.parseString(privKey.hex(), 16)
    // 'X = xG
    val basePubKey = g * x
    // X = X * H(password)
    val pubKey = basePubKey * q.parseString(password.sha256().hex(), 16)
    val trustedContactPasswordAuthenticatedKey =
      AppKeyImpl(
        CurveType.SECP256K1,
        PublicKey(pubKey.secSerialize().hex()),
        PrivateKey(x.toByteArray().toByteString())
      )
    // Tweak PC PAKE Key
    val deserializedProtectedCustomerPasswordAuthenticatedKey =
      Point.secDeserialize(
        protectedCustomerPasswordAuthenticatedKey.value.decodeHex()
      )
    val tweakedProtectedCustomerPasswordAuthenticatedKey =
      deserializedProtectedCustomerPasswordAuthenticatedKey *
        -(
          q.parseString(
            password.sha256().hex(),
            16
          )
        )
    // Derive PAKE shared secret
    val sharedSecret = tweakedProtectedCustomerPasswordAuthenticatedKey * x
    // Generate key confirmation
    val keyConfirmationString = "SocRecKeyConfirmation".encodeUtf8()
    val keyConfirmation = keyConfirmationString.hmacSha256(sharedSecret.secSerialize())
    // Generate expanded shared secret key for the specified length
    val sharedSecretKey =
      expandKey(
        sharedSecret.secSerialize(),
        length
      )

    return EstablishSecureChannelOutput(
      trustedContactPasswordAuthenticatedKey = trustedContactPasswordAuthenticatedKey,
      keyConfirmation = keyConfirmation,
      sharedSecretKey = sharedSecretKey
    )
  }

  private fun decryptSecureChannel(
    password: ByteString,
    protectedCustomerPasswordAuthenticatedKey: PrivateKey,
    trustedContactPasswordAuthenticatedKey: PublicKey,
    keyConfirmation: ByteString,
    sealedData: XCiphertext,
  ): Result<ByteString, SocRecCryptoError> {
    // Tweak TC PAKE Key
    val deserializedTweakedTrustedContactPasswordAuthenticatedKey =
      Point.secDeserialize(
        trustedContactPasswordAuthenticatedKey.value.decodeHex()
      )
    val deserializedProtectedCustomerAuthenticatedPrivKey =
      q.parseString(
        protectedCustomerPasswordAuthenticatedKey.bytes.hex(),
        16
      )
    val tweakedTrustedContactPasswordAuthenticatedKey =
      deserializedTweakedTrustedContactPasswordAuthenticatedKey *
        -(
          q.parseString(
            password.sha256().hex(),
            16
          )
        )
    // Generate PAKE shared secret
    val sharedSecret =
      tweakedTrustedContactPasswordAuthenticatedKey *
        deserializedProtectedCustomerAuthenticatedPrivKey
    // Validate key confirmation
    val keyConfirmationString = "SocRecKeyConfirmation".encodeUtf8()
    val calculatedKeyConfirmation = keyConfirmationString.hmacSha256(sharedSecret.secSerialize())
    if (calculatedKeyConfirmation != keyConfirmation) {
      return Err(
        SocRecCryptoError.KeyConfirmationFailed(
          IllegalArgumentException("Key confirmation mismatch")
        )
      )
    }
    // Decrypt sealed data
    val expandedKey =
      expandKey(
        sharedSecret.secSerialize(),
        sealedData.toXSealedData().ciphertext.size
      )
    return Ok(sealedData.toXSealedData().ciphertext.xorWith(expandedKey))
  }

  private object Secp256k1 {
    val P =
      BigInteger.parseString(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",
        16
      )
    val Q =
      BigInteger.parseString(
        "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
        16
      )
    val Gx =
      BigInteger.parseString(
        "79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798",
        16
      )
    val Gy =
      BigInteger.parseString(
        "483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8",
        16
      )

    val creator = ModularBigInteger.creatorForModulo(P)
    val modGx = creator.fromBigInteger(Gx)
    val modGy = creator.fromBigInteger(Gy)

    fun g() = Point(modGx, modGy)
  }

  private class Point(x: ModularBigInteger? = null, y: ModularBigInteger? = null) {
    val x: ModularBigInteger?
    val y: ModularBigInteger?

    init {
      val p = Secp256k1.P
      require((x == null || x.modulus == p) && (y == null || y.modulus == p)) {
        "Moduli do not match"
      }
      this.x = x
      this.y = y
    }

    companion object {
      fun secDeserialize(hexPublicKey: ByteString): Point {
        val p = Secp256k1.P
        val creator = ModularBigInteger.creatorForModulo(p)
        // Parse the parity byte
        val isEven = hexPublicKey[0].toInt() == 2
        // Parse the x coordinate
        val x = BigInteger.parseString(hexPublicKey.substring(1).hex(), 16)
        val xMod = creator.fromBigInteger(x)
        // y^2 = x^3 + 7
        val ySquared = xMod.pow(3) + 7
        // y = (y^2)^((p + 1) / 4) mod p
        val possibleY = ySquared.pow((p + 1) / 4).toBigInteger()

        val evenParity = possibleY.mod(2.toBigInteger()) == BigInteger.ZERO && isEven
        val oddParity = possibleY.mod(2.toBigInteger()) != BigInteger.ZERO && !isEven
        val parityMatch = evenParity || oddParity
        val y = if (parityMatch) possibleY else p - possibleY
        val yMod = creator.fromBigInteger(y)

        return Point(xMod, yMod)
      }
    }

    fun secSerialize(): ByteString {
      // Point at infinity
      if (this.x == null || this.y == null) return "00".decodeHex()
      // Parity byte
      val isEven = this.y.toBigInteger().mod(2.toBigInteger()) == BigInteger.ZERO
      val prefix = if (isEven) "02" else "03"
      // x coordinate
      val xBytes = this.x.toByteArray()

      return (
        prefix.decodeHex().toByteArray() + xBytes
      ).toByteString()
    }

    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      if (other == null || other !is Point) return false

      return x == other.x && y == other.y
    }

    operator fun unaryMinus(): Point {
      if (this.x == null || this.y == null) return this
      if (this.x.isZero() && this.y.isZero()) return this
      val creator = ModularBigInteger.creatorForModulo(Secp256k1.P)
      val negY = creator.fromBigInteger(Secp256k1.P - this.y.toBigInteger())

      return Point(this.x, negY)
    }

    operator fun plus(other: Point): Point {
      // Points at infinity
      if (this.x == null || this.y == null) return other
      if (other.x == null || other.y == null) return this
      // Tangent line is vertical
      if (this == other && this.y.isZero()) {
        return Point()
      }
      // Opposite points
      if (this.x == other.x && this.y != other.y) return Point()

      // Same point
      if (this == other) {
        val s = (this.x * this.x * 3) / (this.y * 2)
        val x = s * s - this.x * 2
        val y = s * (this.x - x) - this.y
        return Point(x, y)
      }

      // Different points
      val s = (other.y - this.y) / (other.x - this.x)
      val x = s * s - this.x - other.x
      val y = s * (this.x - x) - this.y
      return Point(x, y)
    }

    operator fun times(scalar: ModularBigInteger): Point {
      var currentScalar = scalar.toBigInteger()
      var currentPoint = this
      var sumPoint = Point()

      while (currentScalar != BigInteger.ZERO) {
        if (currentScalar and BigInteger.ONE != BigInteger.ZERO) {
          sumPoint += currentPoint
        }
        currentPoint += currentPoint
        currentScalar = currentScalar shr 1
      }

      return sumPoint
    }

    override fun hashCode(): Int {
      var result = x.hashCode()
      result = 31 * result + y.hashCode()
      return result
    }
  }

  private fun ByteString.xorWith(other: ByteString): ByteString {
    require(size == other.size) { "ByteStrings must be of the same length" }

    val result = ByteArray(size)
    for (i in 0 until size) {
      result[i] =
        (
          this[i].toInt() xor other[i].toInt()
        ).toByte()
    }

    return result.toByteString()
  }

  private fun expandKey(
    key: ByteString,
    length: Int,
  ): ByteString {
    val result = ByteArray(length)
    var currentLength = 0

    var index = BigInteger.ZERO
    while (currentLength < length) {
      val combined = (key.toByteArray() + index.toByteArray())
      val hashBytes = combined.toByteString().sha256().toByteArray()

      for (byte in hashBytes) {
        if (currentLength >= length) break
        result[currentLength] = byte
        currentLength++
      }

      index++
    }

    return result.toByteString()
  }

  private fun randomBytes(): ByteString {
    // Generates a random 256-bit key from an RNG that is not cryptographically
    // secure.
    val randomBytes = ByteArray(32)
    random.nextBytes(randomBytes)

    return randomBytes.toByteString()
  }

  fun sign(
    privateKey: Secp256k1PrivateKey,
    publicKey: Secp256k1PublicKey,
    message: ByteString,
  ): ByteString {
    val (noncePrivKey, noncePubKey) = generateKeyPair()
    // c = H(R || X || m)
    val nonceSer = noncePubKey.value.decodeHex().toByteArray()
    val publicKeySer = publicKey.value.decodeHex().toByteArray()
    val messageSer = message.toByteArray()
    val challenge = (nonceSer + publicKeySer + messageSer).toByteString().sha256()
    // s = r + cx mod q
    val r = q.parseString(noncePrivKey.bytes.hex(), 16)
    val c = q.parseString(challenge.hex(), 16)
    val x = q.parseString(privateKey.bytes.hex(), 16)
    val s = r + c * x

    // σ = (R, s)
    return (nonceSer + s.toByteArray()).toByteString()
  }

  fun verifySig(
    signature: ByteString,
    publicKey: Secp256k1PublicKey,
    message: ByteString,
  ): Boolean {
    val deserializedPubKey = Point.secDeserialize(publicKey.value.decodeHex())
    // (R, s) = σ
    val noncePubKey = Point.secDeserialize(signature.substring(0, 33))
    val s = q.parseString(signature.substring(33).hex(), 16)
    // c = H(R || X || m)
    val nonceSer = noncePubKey.secSerialize().toByteArray()
    val publicKeySer = publicKey.value.decodeHex().toByteArray()
    val messageSer = message.toByteArray()
    val challenge = (nonceSer + publicKeySer + messageSer).toByteString().sha256()

    // sG == R + cX
    val sG = g * s
    val c = q.parseString(challenge.hex(), 16)
    return sG == noncePubKey + deserializedPubKey * c
  }

  fun generateKeyPair(): Pair<Secp256k1PrivateKey, Secp256k1PublicKey> {
    // x ⭠ ℤ_q
    val privKey = randomBytes()
    val x = q.parseString(privKey.hex(), 16)
    // X = xG
    val pubKey = g * x

    return Pair(
      Secp256k1PrivateKey(x.toByteArray().toByteString()),
      Secp256k1PublicKey(pubKey.secSerialize().hex())
    )
  }
}
