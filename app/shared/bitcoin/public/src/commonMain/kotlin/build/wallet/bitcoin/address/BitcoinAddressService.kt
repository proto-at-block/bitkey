package build.wallet.bitcoin.address

import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Result

interface BitcoinAddressService {
  /**
   * Generates a new receiving address. It will be asynchronously registered it to be watched.
   */
  suspend fun generateAddress(account: FullAccount): Result<BitcoinAddress, Throwable>
}
