package build.wallet.bitcoin.address

import build.wallet.bdk.bindings.BdkAddressIndex
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class BitcoinAddressServiceFake : BitcoinAddressService {
  private val defaultAddress = someBitcoinAddress
  var result: Result<BitcoinAddress, Throwable> = Ok(defaultAddress)

  var peekResult: Result<BitcoinAddress, Throwable> = Ok(defaultAddress)

  private var addressIndex: UInt = 0u
  var addressInfoResult: Result<BitcoinAddressInfo, Throwable> =
    Ok(BitcoinAddressInfo(address = defaultAddress, index = addressIndex))

  override suspend fun generateAddress(addressIndex: BdkAddressIndex) =
    when (addressIndex) {
      BdkAddressIndex.LastUnused -> result
      BdkAddressIndex.New -> result
      is BdkAddressIndex.Peek -> peekResult
    }

  override suspend fun generateAddressInfo(): Result<BitcoinAddressInfo, Throwable> {
    return addressInfoResult
  }

  fun reset() {
    result = Ok(defaultAddress)
    peekResult = Ok(someBitcoinAddress)
    addressIndex = 0u
    addressInfoResult = Ok(BitcoinAddressInfo(address = defaultAddress, index = addressIndex))
  }
}
