package build.wallet.bitcoin.address

import com.github.michaelbull.result.Result

interface BitcoinAddressService {
  /**
   * Generates a new receiving address. It will be asynchronously registered it to be watched.
   */
  suspend fun generateAddress(): Result<BitcoinAddress, Throwable>
}
