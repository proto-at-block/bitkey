package build.wallet.keybox.keys

import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitkey.spending.SpendingKeypair
import com.github.michaelbull.result.Result

interface SpendingKeyGenerator {
  /**
   * Generates a new spending keypair for given [network] type.
   *
   * Note that the private key is not stored anywhere, so it is the responsibility of the caller
   * to store it securely.
   */
  suspend fun generate(network: BitcoinNetworkType): Result<SpendingKeypair, Throwable>
}
