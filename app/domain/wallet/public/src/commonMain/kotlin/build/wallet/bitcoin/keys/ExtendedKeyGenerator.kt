package build.wallet.bitcoin.keys

import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Result

interface ExtendedKeyGenerator {
  /**
   * Generates a new bitcoin extended keypair for segwit wallet (BIP-84).
   *
   * Note that the private key is not persisted/stored anywhere by this component.
   */
  suspend fun generate(network: BitcoinNetworkType): Result<DescriptorKeypair, Error>
}
