package build.wallet.bitkey.auth

import build.wallet.encrypt.Secp256k1PrivateKey

/**
 * Represents a private
 * Used for performing authentication with the server.
 */
interface AuthPrivateKey {
  val key: Secp256k1PrivateKey
}
