package build.wallet.bitcoin.address

import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Result

interface BitcoinAddressParser {
  /**
   * Parse given string into [BitcoinAddress], return `null` if address is invalid.
   *
   * See [BitcoinAddress] for supported address types.
   *
   */
  fun parse(
    address: String,
    network: BitcoinNetworkType,
  ): Result<BitcoinAddress, BitcoinAddressParserError>

  sealed class BitcoinAddressParserError : Error() {
    /** User passed an empty string to the Address parser **/
    data object BlankAddress : BitcoinAddressParserError()

    /** Address user passed to parser does not match desired network **/
    data object InvalidNetwork : BitcoinAddressParserError()

    /**
     * Underneath the hood, BDK uses rust-bitcoin's FromStr to parse addresses. If it returns an
     * error, it would be because it does not recognize the script type the address represents.
     **/
    data class InvalidScript(override val cause: Throwable) : BitcoinAddressParserError()

    data object Unknown : BitcoinAddressParserError()
  }
}
