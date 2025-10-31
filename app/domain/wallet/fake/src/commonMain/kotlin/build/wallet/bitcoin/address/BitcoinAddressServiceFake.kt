package build.wallet.bitcoin.address

import build.wallet.bdk.bindings.BdkAddressIndex
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BitcoinAddressServiceFake : BitcoinAddressService {
  private val defaultAddress = someBitcoinAddress
  var result: Result<BitcoinAddress, Throwable> = Ok(defaultAddress)

  var peekResult: Result<BitcoinAddress, Throwable> = Ok(defaultAddress)

  override suspend fun generateAddress(addressIndex: BdkAddressIndex) =
    when (addressIndex) {
      BdkAddressIndex.LastUnused -> result
      BdkAddressIndex.New -> result
      is BdkAddressIndex.Peek -> peekResult
    }

  fun reset() {
    result = Ok(defaultAddress)
    peekResult = Ok(someBitcoinAddress)
  }
}
