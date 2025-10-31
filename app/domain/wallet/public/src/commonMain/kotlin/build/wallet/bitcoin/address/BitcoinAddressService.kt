package build.wallet.bitcoin.address

import build.wallet.bdk.bindings.BdkAddressIndex
import com.github.michaelbull.result.Result

interface BitcoinAddressService {
  /**
   * Generates a new receiving address. It will be asynchronously registered it to be watched.
   */
  suspend fun generateAddress(
    addressIndex: BdkAddressIndex = BdkAddressIndex.New,
  ): Result<BitcoinAddress, Throwable>
}
