package build.wallet.statemachine.data.keybox.address

import build.wallet.bitcoin.address.someBitcoinAddress
import com.github.michaelbull.result.Ok

val KeyboxAddressDataMock =
  KeyboxAddressData(
    latestAddress = someBitcoinAddress,
    generateAddress = { onResult ->
      onResult(Ok(someBitcoinAddress))
    }
  )
