package build.wallet.bdk

import build.wallet.bdk.bindings.BdkAddress
import build.wallet.bdk.bindings.BdkAddressBuilder
import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bdk.bindings.BdkResult
import build.wallet.bdk.bindings.BdkScript

class BdkAddressBuilderImpl : BdkAddressBuilder {
  override fun build(
    address: String,
    bdkNetwork: BdkNetwork,
  ): BdkResult<BdkAddress> {
    return runCatchingBdkError {
      BdkAddressImpl(
        ffiAddress =
          FfiAddress(
            address = address,
            network = bdkNetwork.ffiNetwork
          )
      )
    }
  }

  override fun build(
    script: BdkScript,
    network: BdkNetwork,
  ): BdkResult<BdkAddress> {
    return runCatchingBdkError {
      BdkAddressImpl(
        ffiAddress =
          FfiAddress.fromScript(
            script = script.ffiScript,
            network = network.ffiNetwork
          )
      )
    }
  }
}
