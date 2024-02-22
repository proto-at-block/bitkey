package build.wallet.bitcoin.bdk

import build.wallet.bdk.bindings.BdkAddressInfo
import build.wallet.bdk.bindings.BdkAddressMock

val BdkAddressInfoMock =
  BdkAddressInfo(
    address = BdkAddressMock(),
    index = 1
  )
