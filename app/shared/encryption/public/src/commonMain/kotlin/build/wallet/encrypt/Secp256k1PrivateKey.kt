package build.wallet.encrypt

import build.wallet.crypto.CurveType
import build.wallet.crypto.KeyPurpose
import build.wallet.crypto.PrivateKey
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

fun <T> PrivateKey<T>.toSecp256k1PrivateKey(): Secp256k1PrivateKey where T : KeyPurpose, T : CurveType.Secp256K1 =
  Secp256k1PrivateKey(bytes)

fun <T> Secp256k1PrivateKey.toPrivateKey(): PrivateKey<T> where T : KeyPurpose, T : CurveType.Secp256K1 =
  PrivateKey(bytes)
