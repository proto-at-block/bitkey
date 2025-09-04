package build.wallet.bitcoin.address

import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BitcoinAddressServiceFake : BitcoinAddressService {
  private val defaultAddress = someBitcoinAddress
  var result: Result<BitcoinAddress, Throwable> = Ok(defaultAddress)

  override suspend fun generateAddress() = result

  fun reset() {
    result = Ok(defaultAddress)
  }
}
