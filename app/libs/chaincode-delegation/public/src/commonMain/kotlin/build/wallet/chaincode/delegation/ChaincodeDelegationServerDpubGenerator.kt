package build.wallet.chaincode.delegation

import build.wallet.bitcoin.BitcoinNetworkType

/**
 * Shared interface for generating the server account descriptor public key from the Rust core
*/
interface ChaincodeDelegationServerDpubGenerator {
  /**
   * Generates a server account descriptor public key for the given network and server root public key.
   * @param network The network to generate the server descriptor public key for.
   * @param serverRootPublicKey The server root public key to generate the server descriptor public key for. Returned
   * from /api/v2/accounts
   * @return The server descriptor public key.
   */
  fun generate(
    network: BitcoinNetworkType,
    serverRootPublicKey: String,
  ): String
}
