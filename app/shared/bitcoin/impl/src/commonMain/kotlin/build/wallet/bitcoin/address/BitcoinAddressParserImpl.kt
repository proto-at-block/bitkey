package build.wallet.bitcoin.address

import build.wallet.bdk.bindings.BdkAddress
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bitcoin.BitcoinNetworkType
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError.BlankAddress
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError.InvalidNetwork
import build.wallet.bitcoin.address.BitcoinAddressParser.BitcoinAddressParserError.InvalidScript
import build.wallet.bitcoin.bdk.bdkNetwork
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.flatMap
import com.github.michaelbull.result.mapError

class BitcoinAddressParserImpl(
  val addressBuilder: BdkAddressBuilder,
) : BitcoinAddressParser {
  override fun parse(
    address: String,
    network: BitcoinNetworkType,
  ): Result<BitcoinAddress, BitcoinAddressParserError> =
    binding {
      if (address.isBlank()) {
        Err(BlankAddress)
      } else {
        addressBuilder.build(address, network.bdkNetwork)
          .result
          .mapError { InvalidScript(it) }
          .flatMap { validateNetwork(it, network) }
      }.bind()
    }

  private fun validateNetwork(
    address: BdkAddress,
    network: BitcoinNetworkType,
  ): Result<BitcoinAddress, BitcoinAddressParserError> {
    return address.isValidForNetwork(network.bdkNetwork).let { isValid ->
      if (isValid) {
        Ok(BitcoinAddress(address.asString()))
      } else {
        Err(InvalidNetwork)
      }
    }
  }
}
