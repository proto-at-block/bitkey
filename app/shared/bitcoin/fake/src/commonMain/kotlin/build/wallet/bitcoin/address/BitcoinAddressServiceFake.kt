package build.wallet.bitcoin.address

import build.wallet.bitkey.account.FullAccount
import com.github.michaelbull.result.Ok

class BitcoinAddressServiceFake : BitcoinAddressService {
  private val defaultAddress = someBitcoinAddress
  var result = Ok(defaultAddress)

  override suspend fun generateAddress(account: FullAccount) = result

  fun reset() {
    result = Ok(defaultAddress)
  }
}
