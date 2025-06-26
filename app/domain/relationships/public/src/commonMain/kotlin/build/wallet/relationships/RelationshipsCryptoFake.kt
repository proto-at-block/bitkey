package build.wallet.relationships

import build.wallet.bitcoin.AppPrivateKeyDao
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.bitkey.hardware.AppGlobalAuthKeyHwSignature
import build.wallet.bitkey.hardware.HwAuthPublicKey
import build.wallet.bitkey.keys.app.AppKey
import build.wallet.bitkey.relationships.*
import build.wallet.bitkey.socrec.ProtectedCustomerRecoveryPakeKey
import build.wallet.bitkey.socrec.SocRecKey
import build.wallet.bitkey.socrec.TrustedContactRecoveryPakeKey
import build.wallet.crypto.CurveType
import build.wallet.crypto.PakeKey
import build.wallet.crypto.PrivateKey
import build.wallet.crypto.PublicKey
import build.wallet.encrypt.*
import build.wallet.ensure
import build.wallet.relationships.RelationshipsCryptoError.*
import com.github.michaelbull.result.*
import com.github.michaelbull.result.coroutines.coroutineBinding
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
 *
 * @param messageSigner used to create a key certificate. Note that if this is `null`,
 *  then [generateKeyCertificate] and [sign] will crash.
 * @param signatureVerifier used to verify a key certificate. Note that if this is `null`,
 *  then [verifyKeyCertificate] and [verifySig] will crash.
 */
class RelationshipsCryptoFake(
  private val messageSigner: MessageSigner? = null,
  private val signatureVerifier: SignatureVerifier? = null,
  private val appPrivateKeyDao: AppPrivateKeyDao? = null,
  private val random: Random = Random(0),
) : RelationshipsCrypto {
  private val g = Secp256k1.g()
  private val q = ModularBigInteger.creatorForModulo(Secp256k1.Q)

  /** Generates a usable but insecure key pair */
  private fun generateProtectedCustomerIdentityKey():
    Result<AppKey<ProtectedCustomerIdentityKey>, RelationshipsCryptoError> =
    Ok(generateAsymmetricKeyUnwrapped())

  /** Generates a usable but insecure key pair */
  override fun generateDelegatedDecryptionKey(): Result<AppKey<DelegatedDecryptionKey>, RelationshipsCryptoError> =
    Ok(generateAsymmetricKeyUnwrapped())

  override fun generateProtectedCustomerEnrollmentPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerEnrollmentPakeKey>, RelationshipsCryptoError> {
    return Ok(generatePakeKey(password))
  }

  override fun generateProtectedCustomerRecoveryPakeKey(
    password: PakeCode,
  ): Result<AppKey<ProtectedCustomerRecoveryPakeKey>, RelationshipsCryptoError> {
    return Ok(
      generatePakeKey(password)
    )
  }

  private fun <T : PakeKey> generatePakeKey(password: PakeCode): AppKey<T> {
    // x ⭠ ℤ_q
    val privKey = randomBytes()
    val x = q.parseString(privKey.hex(), 16)
    // 'X = xG
    val basePubKey = g * x
    // X = 'X * H(password)
    val pubKey = basePubKey * q.parseString(password.bytes.sha256().hex(), 16)

    return AppKey(
      PublicKey(pubKey.secSerialize().hex()),
      PrivateKey(x.toByteArray().toByteString())
    )
  }

  override fun encryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: PublicKey<ProtectedCustomerEnrollmentPakeKey>,
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
  ): Result<EncryptDelegatedDecryptionKeyOutput, RelationshipsCryptoError> {
    val trustedContactIdentityKeyBytes = delegatedDecryptionKey.value.decodeHex()
    // Generate PAKE keys
    val secureChannelOutput =
      establishSecureChannel<ProtectedCustomerEnrollmentPakeKey, TrustedContactEnrollmentPakeKey>(
        password,
        protectedCustomerEnrollmentPakeKey,
        trustedContactIdentityKeyBytes.size
      )
    // Encrypt RC Identity Key
    val trustedContactIdentityKeyCiphertext =
      trustedContactIdentityKeyBytes.xorWith(secureChannelOutput.sharedSecretKey)

    return Ok(
      EncryptDelegatedDecryptionKeyOutput(
        sealedDelegatedDecryptionKey =
          XSealedData(
            XSealedData.Header(algorithm = "RelationshipsCryptoFake"),
            ciphertext = trustedContactIdentityKeyCiphertext,
            nonce = XNonce(ByteString.EMPTY)
          ).toOpaqueCiphertext(),
        trustedContactEnrollmentPakeKey =
          secureChannelOutput.trustedContactPasswordAuthenticatedKey.publicKey,
        keyConfirmation = secureChannelOutput.keyConfirmation
      )
    )
  }

  override fun decryptDelegatedDecryptionKey(
    password: PakeCode,
    protectedCustomerEnrollmentPakeKey: AppKey<ProtectedCustomerEnrollmentPakeKey>,
    encryptDelegatedDecryptionKeyOutput: EncryptDelegatedDecryptionKeyOutput,
  ): Result<PublicKey<DelegatedDecryptionKey>, RelationshipsCryptoError> =
    binding {
      val trustedContactIdentityKey =
        decryptSecureChannel(
          password,
          protectedCustomerEnrollmentPakeKey.privateKey,
          encryptDelegatedDecryptionKeyOutput.trustedContactEnrollmentPakeKey,
          encryptDelegatedDecryptionKeyOutput.keyConfirmation,
          encryptDelegatedDecryptionKeyOutput.sealedDelegatedDecryptionKey
        ).bind()

      PublicKey(trustedContactIdentityKey.hex())
    }

  // Key certificates to automatically reject. For testing purposes.
  val invalidCertificates = mutableSetOf<TrustedContactKeyCertificate>()

  // Key certificates to automatically accept. For testing purposes.
  val validCertificates = mutableSetOf<TrustedContactKeyCertificate>()

  override fun verifyKeyCertificate(
    keyCertificate: TrustedContactKeyCertificate,
    hwAuthKey: HwAuthPublicKey?,
    appGlobalAuthKey: PublicKey<AppGlobalAuthKey>?,
  ): Result<PublicKey<DelegatedDecryptionKey>, RelationshipsCryptoError> {
    if (hwAuthKey == null && appGlobalAuthKey == null) {
      return Err(RelationshipsCryptoError.AuthKeysNotPresent)
    }

    return binding {
      ensure(keyCertificate !in invalidCertificates) {
        RelationshipsCryptoError.KeyCertificateVerificationFailed(
          IllegalArgumentException("Invalid key certificate")
        )
      }

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

      // Ensure at least one key matches a trusted key
      ensure(
        keyCertificate in validCertificates || (
          signatureVerifier!!.verifyEcdsaResult(
            signature = keyCertificate.appAuthGlobalKeyHwSignature.value,
            publicKey = hwEndorsementKey.pubKey,
            message = keyCertificate.appGlobalAuthPublicKey.value.encodeUtf8()
          ).mapError { RelationshipsCryptoError.KeyCertificateVerificationFailed(it) }.bind() ||
            !signatureVerifier.verifyEcdsaResult(
              signature = keyCertificate.trustedContactIdentityKeyAppSignature.value,
              publicKey = appEndorsementKey.toSecp256k1PublicKey(),
              message = keyCertificate.delegatedDecryptionKey.value.encodeUtf8()
            ).mapError { RelationshipsCryptoError.KeyCertificateVerificationFailed(it) }.bind()
        )
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
      val appAuthPrivateKey = appPrivateKeyDao!!
        .getAsymmetricPrivateKey(appGlobalAuthKey)
        .mapError(::ErrorGettingPrivateKey)
        .toErrorIfNull { RelationshipsCryptoError.PrivateKeyMissing }
        .bind()

      val appSignature =
        sign(
          privateKey = appAuthPrivateKey.toSecp256k1PrivateKey(),
          message = delegatedDecryptionKey.value.encodeUtf8()
        ).hex().let(::TcIdentityKeyAppSignature)

      TrustedContactKeyCertificate(
        delegatedDecryptionKey = delegatedDecryptionKey,
        hwAuthPublicKey = hwAuthKey,
        appGlobalAuthPublicKey = appGlobalAuthKey,
        appAuthGlobalKeyHwSignature = appGlobalAuthKeyHwSignature,
        trustedContactIdentityKeyAppSignature = appSignature
      )
    }

  override fun <T> generateAsymmetricKey(): Result<AppKey<T>, RelationshipsCryptoError> where T : SocRecKey, T : CurveType.Curve25519 =
    Ok(generateAsymmetricKeyUnwrapped())

  private fun <T> generateAsymmetricKeyUnwrapped(): AppKey<T> where T : SocRecKey, T : CurveType.Curve25519 {
    val (privKey, pubKey) = generateKeyPair()

    return AppKey(
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
  ): Result<EncryptPrivateKeyMaterialOutput, RelationshipsCryptoError> {
    val privateKeyEncryptionKey = randomBytes()
    val expandedKey = expandKey(privateKeyEncryptionKey, privateKeyMaterial.size)
    val privateKeyMaterialCiphertext = privateKeyMaterial.xorWith(expandedKey)
    return Ok(
      EncryptPrivateKeyMaterialOutput(
        sealedPrivateKeyMaterial =
          XSealedData(
            XSealedData.Header(algorithm = "RelationshipsCryptoFake"),
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
   * Generates a ciphertext with an insecure encryption algorithm (i.e. naive key
   * expansion and XOR).
   */
  override fun encryptDescriptor(
    dek: PrivateKeyEncryptionKey,
    descriptor: ByteString,
  ): Result<XCiphertext, RelationshipsCryptoError> {
    val expandedKey = expandKey(dek.raw, descriptor.size)
    val descriptorCiphertext = descriptor.xorWith(expandedKey)
    return Ok(
      XSealedData(
        XSealedData.Header(algorithm = "RelationshipsCryptoFake"),
        ciphertext = descriptorCiphertext,
        nonce = XNonce(ByteString.EMPTY)
      ).toOpaqueCiphertext()
    )
  }

  /**
   * Generates a ciphertext with an insecure Diffie-Hellman derivation and an
   * insecure encryption algorithm (i.e. naive key expansion and XOR).
   */
  override fun encryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: PublicKey<DelegatedDecryptionKey>,
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
  ): Result<XCiphertext, RelationshipsCryptoError> =
    binding {
      require(privateKeyEncryptionKey.key is SymmetricKeyFake)
      val protectedCustomerIdentityKey = generateProtectedCustomerIdentityKey().bind()
      val keyMat = (privateKeyEncryptionKey.key as SymmetricKeyFake).raw
      val deserializedPubKey =
        Point.secDeserialize(
          delegatedDecryptionKey.value.decodeHex()
        )
      val deserializedPrivKey =
        q.parseString(
          protectedCustomerIdentityKey.privateKey.bytes.hex(),
          16
        )
      val sharedSecret = deserializedPubKey * deserializedPrivKey

      val expandedKey =
        expandKey(
          sharedSecret.secSerialize(),
          privateKeyEncryptionKey.length
        )
      val privateKeyEncryptionKeyCiphertext = keyMat.xorWith(expandedKey)

      XSealedData(
        header = XSealedData.Header(algorithm = "RelationshipsCryptoFake", version = 2),
        ciphertext = privateKeyEncryptionKeyCiphertext,
        nonce = XNonce(ByteString.EMPTY),
        publicKey = protectedCustomerIdentityKey.publicKey
      ).toOpaqueCiphertext()
    }

  override fun decryptPrivateKeyEncryptionKey(
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): PrivateKeyEncryptionKey {
    TODO("Not yet implemented")
  }

  override fun decryptPrivateKeyMaterial(
    privateKeyEncryptionKey: PrivateKeyEncryptionKey,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, RelationshipsCryptoError> {
    TODO("Not yet implemented")
  }

  override fun transferPrivateKeyEncryptionKeyEncryption(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: PublicKey<ProtectedCustomerRecoveryPakeKey>,
    delegatedDecryptionKey: AppKey<DelegatedDecryptionKey>,
    sealedPrivateKeyEncryptionKey: XCiphertext,
  ): Result<DecryptPrivateKeyEncryptionKeyOutput, RelationshipsCryptoError> {
    val sealedPrivateKeyEncryptionKeyData = sealedPrivateKeyEncryptionKey.toXSealedData()
    if (sealedPrivateKeyEncryptionKeyData.header.version != 2) {
      return Err(UnsupportedXCiphertextVersion)
    }
    // Generate PAKE keys
    val secureChannelOutput =
      establishSecureChannel<ProtectedCustomerRecoveryPakeKey, TrustedContactRecoveryPakeKey>(
        password,
        protectedCustomerRecoveryPakeKey,
        sealedPrivateKeyEncryptionKey.toXSealedData().ciphertext.size
      )
    val protectedCustomerIdentityPubKey = sealedPrivateKeyEncryptionKeyData.publicKey
      ?: return Err(PublicKeyMissing)
    val deserializedIdentityPubKey =
      Point.secDeserialize(
        protectedCustomerIdentityPubKey.value.decodeHex()
      )
    val deserializedIdentityPrivKey =
      q.parseString(
        delegatedDecryptionKey.privateKey.bytes.hex(),
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
        trustedContactRecoveryPakeKey = secureChannelOutput.trustedContactPasswordAuthenticatedKey.publicKey,
        keyConfirmation = secureChannelOutput.keyConfirmation,
        sealedPrivateKeyEncryptionKey =
          XSealedData(
            XSealedData.Header(algorithm = "RelationshipsCryptoFake"),
            ciphertext = pakeSealedPrivateKeyEncryptionKey,
            nonce = XNonce(ByteString.EMPTY)
          ).toOpaqueCiphertext()
      )
    )
  }

  override fun decryptPrivateKeyMaterial(
    password: PakeCode,
    protectedCustomerRecoveryPakeKey: AppKey<ProtectedCustomerRecoveryPakeKey>,
    decryptPrivateKeyEncryptionKeyOutput: DecryptPrivateKeyEncryptionKeyOutput,
    sealedPrivateKeyMaterial: XCiphertext,
  ): Result<ByteString, RelationshipsCryptoError> =
    // Decrypt PKEK
    binding {
      val privateKeyEncryptionKey =
        decryptSecureChannel(
          password,
          protectedCustomerRecoveryPakeKey.privateKey,
          decryptPrivateKeyEncryptionKeyOutput.trustedContactRecoveryPakeKey,
          decryptPrivateKeyEncryptionKeyOutput.keyConfirmation,
          decryptPrivateKeyEncryptionKeyOutput.sealedPrivateKeyEncryptionKey
        ).bind()
      // Decrypt PKMat
      val expandedPrivateKeyEncryptionKey =
        expandKey(privateKeyEncryptionKey, sealedPrivateKeyMaterial.toXSealedData().ciphertext.size)

      sealedPrivateKeyMaterial.toXSealedData().ciphertext.xorWith(expandedPrivateKeyEncryptionKey)
    }

  private data class EstablishSecureChannelOutput<T : TrustedContactPakeKey>(
    val trustedContactPasswordAuthenticatedKey: AppKey<T>,
    val keyConfirmation: ByteString,
    val sharedSecretKey: ByteString,
  )

  private fun <P : ProtectedCustomerPakeKey, T : TrustedContactPakeKey> establishSecureChannel(
    password: PakeCode,
    protectedCustomerPasswordAuthenticatedKey: PublicKey<P>,
    length: Int,
  ): EstablishSecureChannelOutput<T> {
    // Generate RC PAKE Key
    // x ⭠ ℤ_q
    val privKey = randomBytes()
    val x = q.parseString(privKey.hex(), 16)
    // 'X = xG
    val basePubKey = g * x
    // X = X * H(password)
    val (passwordHashInt, invPasswordHashInt) = derivePasswordHashIntegers(password.bytes)
    val pubKey = basePubKey * passwordHashInt
    val trustedContactPasswordAuthenticatedKey =
      AppKey<T>(
        PublicKey(pubKey.secSerialize().hex()),
        PrivateKey(x.toByteArray().toByteString())
      )
    // Tweak PC PAKE Key
    val deserializedProtectedCustomerPasswordAuthenticatedKey =
      Point.secDeserialize(
        protectedCustomerPasswordAuthenticatedKey.value.decodeHex()
      )
    val tweakedProtectedCustomerPasswordAuthenticatedKey =
      deserializedProtectedCustomerPasswordAuthenticatedKey * invPasswordHashInt
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

  private fun <P : ProtectedCustomerPakeKey, T : TrustedContactPakeKey> decryptSecureChannel(
    password: PakeCode,
    protectedCustomerPasswordAuthenticatedKey: PrivateKey<P>,
    trustedContactPasswordAuthenticatedKey: PublicKey<T>,
    keyConfirmation: ByteString,
    sealedData: XCiphertext,
  ): Result<ByteString, RelationshipsCryptoError> {
    // Tweak RC PAKE Key
    val deserializedTrustedContactPasswordAuthenticatedKey =
      Point.secDeserialize(
        trustedContactPasswordAuthenticatedKey.value.decodeHex()
      )
    val deserializedProtectedCustomerAuthenticatedPrivKey =
      q.parseString(
        protectedCustomerPasswordAuthenticatedKey.bytes.hex(),
        16
      )
    val (_, invPasswordHashInt) = derivePasswordHashIntegers(password.bytes)
    val tweakedTrustedContactPasswordAuthenticatedKey =
      deserializedTrustedContactPasswordAuthenticatedKey * invPasswordHashInt
    // Generate PAKE shared secret
    val sharedSecret =
      tweakedTrustedContactPasswordAuthenticatedKey *
        deserializedProtectedCustomerAuthenticatedPrivKey
    // Validate key confirmation
    val keyConfirmationString = "SocRecKeyConfirmation".encodeUtf8()
    val calculatedKeyConfirmation = keyConfirmationString.hmacSha256(sharedSecret.secSerialize())
    if (calculatedKeyConfirmation != keyConfirmation) {
      return Err(
        RelationshipsCryptoError.KeyConfirmationFailed(
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

  private fun derivePasswordHashIntegers(
    password: ByteString,
  ): Pair<ModularBigInteger, ModularBigInteger> {
    val passwordHash = password.sha256().hex()
    val passwordHashInt = q.parseString(passwordHash, 16)
    // Compute modular multiplicative inverse of the password hash using Fermat's Little Theorem
    val invPasswordHashInt = passwordHashInt.pow(Secp256k1.Q - 2)

    return Pair(passwordHashInt, invPasswordHashInt)
  }

  fun sign(
    privateKey: Secp256k1PrivateKey,
    message: ByteString,
  ): ByteString = messageSigner!!.sign(message, privateKey).decodeHex()

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

  suspend fun generateAppAuthKeypair(): AppKey<AppGlobalAuthKey> {
    val (privKey, pubKey) = generateKeyPair()
    return AppKey<AppGlobalAuthKey>(
      publicKey = pubKey.toPublicKey(),
      privateKey = privKey.toPrivateKey()
    ).also {
      appPrivateKeyDao!!.storeAppKeyPair(it)
    }
  }

  fun reset() {
    validCertificates.clear()
    invalidCertificates.clear()
  }
}
