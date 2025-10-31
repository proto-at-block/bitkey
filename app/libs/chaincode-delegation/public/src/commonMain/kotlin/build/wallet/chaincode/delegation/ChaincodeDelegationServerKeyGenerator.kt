package build.wallet.chaincode.delegation

import build.wallet.bitcoin.BitcoinNetworkType

/**
 * Shared interface for generating the server keys from the Rust core
*/
interface ChaincodeDelegationServerKeyGenerator {
  /**
   * Generates a server root extended public key given the server raw public key
   * @param network bitcoin network flavor
   * @param serverRootPublicKey The server root raw public key, returned from /api/v2/accounts
   * @return The server root extended public key (xpub)
   */
  fun generateRootExtendedPublicKey(
    network: BitcoinNetworkType,
    serverRootPublicKey: String,
  ): String

  /**
   * Generates the server account descriptor public key for the given network and server root public key.
   * @param network bitcoin network flavor
   * @param serverRootExtendedPublicKey the server root extended public key
   * @return The public server descriptor (dpub)
   */
  fun generateAccountDescriptorPublicKey(
    network: BitcoinNetworkType,
    serverRootExtendedPublicKey: String,
  ): String
}
