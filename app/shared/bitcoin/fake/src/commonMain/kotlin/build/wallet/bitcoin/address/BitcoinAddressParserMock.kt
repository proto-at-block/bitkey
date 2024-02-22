package build.wallet.bitcoin.address

import build.wallet.bitcoin.BitcoinNetworkType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BitcoinAddressParserMock : BitcoinAddressParser {
  private val defaultResult = Ok(bitcoinAddressP2TR)
  var parseResult: Result<BitcoinAddress, BitcoinAddressParser.BitcoinAddressParserError> = defaultResult

  override fun parse(
    address: String,
    network: BitcoinNetworkType,
  ): Result<BitcoinAddress, BitcoinAddressParser.BitcoinAddressParserError> {
    return parseResult
  }

  fun reset() {
    parseResult = defaultResult
  }
}
