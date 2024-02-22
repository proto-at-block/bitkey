package build.wallet.encrypt

import dev.zacsweers.redacted.annotations.Redacted
import okio.ByteString

/**
 * Represents a private key for the Secp256k1 elliptic curve.
 *
 * @property bytes The raw byte representation of the private key.
 */
@Redacted
data class Secp256k1PrivateKey(
  val bytes: ByteString,
)
