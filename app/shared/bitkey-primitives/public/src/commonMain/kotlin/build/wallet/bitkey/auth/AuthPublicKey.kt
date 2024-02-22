package build.wallet.bitkey.auth

import build.wallet.encrypt.Secp256k1PublicKey

/**
 * Represents a public extended bitcoin key derived for segwit wallet (BIP-84).
 * Used for performing authentication with the server.
 */
interface AuthPublicKey {
  val pubKey: Secp256k1PublicKey
}
