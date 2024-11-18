package build.wallet.secureenclave

import kotlin.time.Duration

/**
 * Handle to a private key in the secure enclave.
 */
class SeKeyHandle(
  val name: String,
)

/**
 * Public key for a private key in the secure enclave.
 */
class SePublicKey(
  val bytes: ByteArray, // SEC1 uncompressed public key: 0x04 || x || y (see: https://crypto.stackexchange.com/questions/96104/what-is-was-sec1-ecc-public-key-leading-octet-0x01-for)
)

/**
 * An SE backed key pair.
 */
class SeKeyPair(
  val privateKey: SeKeyHandle,
  val publicKey: SePublicKey,
)

/**
 * What the key can be used for.
 */
enum class SeKeyPurpose {
  SIGNING,
  AGREEMENT,
}

/**
 * A set of purposes for a key.
 */
data class SeKeyPurposes(
  val purposes: Set<SeKeyPurpose>,
) {
  companion object {
    // Accepts vararg SeKeyPurpose and converts it to a Set
    fun of(vararg purposes: SeKeyPurpose): SeKeyPurposes = SeKeyPurposes(purposes.toSet())

    // Accepts a collection or a set of SeKeyPurpose
    fun of(purposes: Collection<SeKeyPurpose>): SeKeyPurposes = SeKeyPurposes(purposes.toSet())
  }

  override fun toString(): String =
    if (purposes.isEmpty()) {
      "NONE"
    } else {
      purposes.joinToString(", ") { it.name }
    }
}

/**
 * Authentication requirements imposed on the key.
 */
enum class SeKeyUsageConstraints {
  NONE, // No authentication required for use.
  BIOMETRICS_OR_PIN_REQUIRED,
  PIN_REQUIRED,
}

/**
 * How long the key is valid for; or if it's required for every use.
 */
sealed class SeKeyValidity {
  object RequiredForEveryUse : SeKeyValidity()

  data class ValidForDuration(
    val duration: Duration,
  ) : SeKeyValidity()
}

/**
 * Parameters for generating a key.
 */
data class SeKeySpec(
  val name: String,
  val purposes: SeKeyPurposes,
  val usageConstraints: SeKeyUsageConstraints,
  val validity: SeKeyValidity?, // Only needed if SeKeyUsageConstraints is not NONE, and ONLY valid on Android
)

sealed class SecureEnclaveError : Error() {
  object NoSecureEnclave : SecureEnclaveError()
}

/**
 * This module provides an interface for dealing with keys held in a "secure enclave".
 * IMPORTANT: At present, this module ONLY SUPPORTS P256, because that's all that iOS supports.
 *
 * The backing implementation is platform-specific, but it may be:
 * - A cryptographic coprocessor within the same SoC, like Apple's Secure Enclave.
 * - Trusted Execution Environment (TEE) like TrustZone.
 * - "StrongBox", which is defined in the Android CDD, but requires "a discrete CPU that
 *   shares no cache, DRAM, coprocessors or other core resources with the application processor",
 *   so it's similar to Apple's.
 */
interface SecureEnclave {
  /**
   * Generates a P256 key pair.
   * @param spec The specification for the key.
   * @return The key pair.
   */
  @Throws(Error::class)
  fun generateP256KeyPair(spec: SeKeySpec): SeKeyPair

  /**
   * Acquire the public key for a private key stored in the secure enclave.
   * @param sePrivateKey The private key.
   * @return The key pair.
   */
  @Throws(Error::class)
  fun publicKeyForPrivateKey(sePrivateKey: SeKeyHandle): SePublicKey

  /**
   * Perform a Diffie-Hellman key exchange.
   * @param ourPrivateKey Our private key.
   * @param peerPublicKey The public key of the peer.
   * @return The shared secret.
   */
  @Throws(Error::class)
  fun diffieHellman(
    ourPrivateKey: SeKeyHandle,
    peerPublicKey: SePublicKey,
  ): ByteArray
}
