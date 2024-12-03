package build.wallet.secureenclave

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.annotation.RequiresApi
import build.wallet.catchingResult
import build.wallet.logging.logWarn
import com.github.michaelbull.result.getOrThrow
import java.math.BigInteger
import java.security.*
import java.security.interfaces.ECPublicKey
import java.security.spec.*
import javax.crypto.KeyAgreement

class SecureEnclaveImpl(
  val context: Context,
) : SecureEnclave {
  // This "AndroidKeyStore" is the name of the keystore provider, and must
  // be named exactly that.
  private val androidKeystore = "AndroidKeyStore"

  private val keyStore: KeyStore by lazy {
    KeyStore.getInstance(androidKeystore).apply { load(null) }
  }

  /**
   * Load a private key from the secure enclave.
   */
  private fun loadSePrivateKey(sePrivateKey: SeKeyHandle): PrivateKey =
    keyStore.getKey(sePrivateKey.name, null) as? PrivateKey
      ?: error("No private key found with alias: ${sePrivateKey.name}")

  /**
   * Load a key pair from the secure enclave.
   */
  private fun loadSeKeyPair(sePrivateKey: SeKeyHandle): KeyPair {
    val entry = keyStore.getEntry(sePrivateKey.name, null) as? KeyStore.PrivateKeyEntry
      ?: error("No key found with alias: $sePrivateKey.name")

    val privateKey = entry.privateKey
    val publicKey = entry.certificate.publicKey

    return KeyPair(publicKey, privateKey)
  }

  /**
   * Load a public key. The public key must be over P256 and in SEC1 uncompressed format.
   */
  fun loadSePublicKey(sePublicKey: SePublicKey): ECPublicKey {
    if (sePublicKey.bytes.size != 65) {
      error("Public key is the wrong length: ${sePublicKey.bytes.size}, ${sePublicKey.bytes.toHexString()}")
    }
    if (sePublicKey.bytes[0] != 0x04.toByte()) {
      error("Public key is not in SEC1 uncompressed format")
    }

    val point = ECPoint(
      BigInteger(1, sePublicKey.bytes.copyOfRange(1, 33)),
      BigInteger(1, sePublicKey.bytes.copyOfRange(33, 65))
    )

    // Yes, this is ACTUALLY necessary. The other option is to generate a key, and then grab the
    // params from it.
    val p = BigInteger(
      "ffffffff00000001000000000000000000000000ffffffffffffffffffffffff",
      16
    ) // Prime field
    val a = BigInteger(
      "ffffffff00000001000000000000000000000000fffffffffffffffffffffffc",
      16
    ) // Coefficient a
    val b = BigInteger(
      "5ac635d8aa3a93e7b3ebbd55769886bc651d06b0cc53b0f63bce3c3e27d2604b",
      16
    ) // Coefficient b
    val gX = BigInteger(
      "6b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296",
      16
    ) // Base point G (x)
    val gY = BigInteger(
      "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5",
      16
    ) // Base point G (y)
    val n = BigInteger(
      "ffffffff00000000ffffffffffffffffbce6faada7179e84f3b9cac2fc632551",
      16
    ) // Order of the group
    val h = 1 // Cofactor

    val curve = EllipticCurve(ECFieldFp(p), a, b)
    val ecSpec = ECParameterSpec(curve, ECPoint(gX, gY), n, h)

    val publicKeySpec = ECPublicKeySpec(point, ecSpec)

    val keyFactory = KeyFactory.getInstance("EC")
    return keyFactory.generatePublic(publicKeySpec) as ECPublicKey
  }

  /**
   * Check if a key is AT LEAST backed by a TEE.
   * Handles both Android 11 and below, and Android 12+.
   *
   * @return True if the private key is held in StrongBox or TEE, false otherwise.
   */
  private fun keyIsBackedAtLeastByTEE(keyAlias: String): Boolean {
    val privateKey = keyStore.getKey(keyAlias, null) as? PrivateKey
      ?: error("No private key found with alias: $keyAlias")

    val keyFactory = KeyFactory.getInstance(privateKey.algorithm, androidKeystore)
    val keyInfo = keyFactory.getKeySpec(privateKey, KeyInfo::class.java) as KeyInfo

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      // Android 12+ (API level 31): Use securityLevel
      keyInfo.securityLevel >= KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT
    } else {
      // Android 11 and below: Use isInsideSecureHardware
      keyInfo.isInsideSecureHardware
    }
  }

  /**
   * Convert a set of key purposes to Android KeyStore properties.
   */
  @Throws(InvalidAlgorithmParameterException::class)
  private fun propertiesForPurposes(purposes: SeKeyPurposes): Int {
    var properties = 0

    for (p in purposes.purposes) {
      when (p) {
        SeKeyPurpose.SIGNING -> {
          properties = properties or KeyProperties.PURPOSE_SIGN
        }
        SeKeyPurpose.AGREEMENT -> {
          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            logWarn { "Key agreement is not supported on Android versions below S" }
          } else {
            properties = properties or KeyProperties.PURPOSE_AGREE_KEY
          }
        }
      }
    }

    return properties
  }

  @RequiresApi(Build.VERSION_CODES.R)
  private fun setUserAuthenticationParameters(
    spec: SeKeySpec,
    builder: KeyGenParameterSpec.Builder,
  ) {
    // Is PIN required, or is biometry also allowed?
    val authType = when (spec.usageConstraints) {
      SeKeyUsageConstraints.BIOMETRICS_OR_PIN_REQUIRED ->
        KeyProperties.AUTH_DEVICE_CREDENTIAL or KeyProperties.AUTH_BIOMETRIC_STRONG

      else -> KeyProperties.AUTH_DEVICE_CREDENTIAL
    }

    when (val validity = spec.validity) {
      is SeKeyValidity.RequiredForEveryUse -> {
        builder.setUserAuthenticationParameters(
          0, // 0 indicates authentication required for every use
          authType
        )
      }

      is SeKeyValidity.ValidForDuration -> {
        builder
          .setUserAuthenticationParameters(
            validity.duration.inWholeSeconds
              .toInt(),
            authType
          )
      }

      null -> error("SeKeySpec.usageConstraints is not NONE, but SeKeySpec.validity is null")
    }
  }

  companion object {
    // Java's BigInteger uses signed integers, reserving the most significant bit for the sign.
    // If the MSB of the byte array is 1, Java adds a leading 0x00 byte to indicate the number is positive.
    private fun normalizeCoordinate(coordinate: ByteArray): ByteArray {
      // If the coordinate has a leading 0x00 and is 33 bytes, trim it
      return if (coordinate.size == 33) {
        if (coordinate[0] != 0x00.toByte()) {
          error(
            "Coordinate has a leading 0x00 byte, " +
              "but it's not 33 bytes: ${coordinate.toHexString()}"
          )
        }
        coordinate.copyOfRange(1, 33)
      } else {
        coordinate // Already 32 bytes, no need to trim
      }
    }

    fun encodePublicKeyAsSEC1Uncompressed(publicKey: ECPublicKey): ByteArray {
      val ecPoint = publicKey.w
      val x = ecPoint.affineX
      val y = ecPoint.affineY

      val output = ByteArray(65)
      output[0] = 0x04

      val xBytes = normalizeCoordinate(x.toByteArray())
      val yBytes = normalizeCoordinate(y.toByteArray())
      System.arraycopy(xBytes, 0, output, 1, xBytes.size)
      System.arraycopy(yBytes, 0, output, 1 + xBytes.size, yBytes.size)

      return output
    }
  }

  private fun deleteKey(keyAlias: String) {
    keyStore.deleteEntry(keyAlias)
  }

  override fun generateP256KeyPair(spec: SeKeySpec): SeKeyPair =
    catchingResult {
      val keyPairGenerator = KeyPairGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_EC,
        androidKeystore
      )

      // Explicitly delete the key prior to generation to ensure parity with iOS, even though
      // this isn't strictly necessary.
      deleteKey(spec.name)

      val builder = KeyGenParameterSpec
        .Builder(spec.name, propertiesForPurposes(spec.purposes))
        .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
        // Enrolling a new biometry template requires re-authentication anyway;
        // if the user's phone PIN is compromised, there are bigger problems, so we're erring on the side of preventing
        // possible funds loss scenarios.
        .setInvalidatedByBiometricEnrollment(false)

      // Require StrongBox if available
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_STRONGBOX_KEYSTORE)
      ) {
        builder.setIsStrongBoxBacked(true)
      }

      if (spec.usageConstraints > SeKeyUsageConstraints.NONE) {
        builder.setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
          setUserAuthenticationParameters(spec, builder)
        } else {
          // Prior to Android R, you can't specify key usage restrictions; so PIN or biometrics are both allowed.
          // However, we can still set the validity duration -- just with the older API.
          builder.setUserAuthenticationValidityDurationSeconds(
            spec.validity?.let { validity ->
              when (validity) {
                is SeKeyValidity.RequiredForEveryUse -> 0
                is SeKeyValidity.ValidForDuration ->
                  validity.duration.inWholeSeconds
                    .toInt()
              }
            }
              ?: error("SeKeySpec.usageConstraints is not NONE, but SeKeySpec.validity is null")
          )
        }
      } else if (spec.validity != null) {
        error("SeKeySpec.usageConstraints is NONE, but SeKeySpec.validity is not null")
      }

      keyPairGenerator.initialize(builder.build())
      val keyPair: KeyPair = keyPairGenerator.generateKeyPair()

      if (!keyIsBackedAtLeastByTEE(spec.name)) {
        // If this fails, we should delete the key to prevent future use. If the phone doesn't
        // support secure hardware, you can't use this class.
        deleteKey(spec.name)
        // Throw a specific error so that the caller can know that the problem is lack of
        // necessary hardware.
        throw SecureEnclaveError.NoSecureEnclave
      }

      SeKeyPair(
        privateKey = SeKeyHandle(spec.name),
        publicKey = SePublicKey(encodePublicKeyAsSEC1Uncompressed(keyPair.public as ECPublicKey))
      )
    }.getOrThrow()

  // Convert private key to a SEC1 uncompressed public key
  override fun publicKeyForPrivateKey(sePrivateKey: SeKeyHandle): SePublicKey {
    val keyPair = loadSeKeyPair(sePrivateKey)
    return SePublicKey(encodePublicKeyAsSEC1Uncompressed(keyPair.public as ECPublicKey))
  }

  override fun diffieHellman(
    ourPrivateKey: SeKeyHandle,
    peerPublicKey: SePublicKey,
  ): ByteArray {
    val privateKey = loadSePrivateKey(ourPrivateKey)
    val publicKey = loadSePublicKey(peerPublicKey)

    val keyAgreement = KeyAgreement.getInstance("ECDH")
    keyAgreement.init(privateKey)
    keyAgreement.doPhase(publicKey, true)

    return keyAgreement.generateSecret("RAW").encoded
  }

  override fun loadKeyPair(name: String): SeKeyPair {
    val keyPair = loadSeKeyPair(SeKeyHandle(name))
    return SeKeyPair(
      privateKey = SeKeyHandle(name),
      publicKey = SePublicKey(encodePublicKeyAsSEC1Uncompressed(keyPair.public as ECPublicKey))
    )
  }
}
