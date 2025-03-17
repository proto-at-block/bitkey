package build.wallet.bitcoin.address

import com.github.michaelbull.result.Ok

class BitcoinAddressServiceFake : BitcoinAddressService {
  private val defaultAddress = someBitcoinAddress
  var result = Ok(defaultAddress)

  override suspend fun generateAddress() = result

  fun reset() {
    result = Ok(defaultAddress)
  }
}
