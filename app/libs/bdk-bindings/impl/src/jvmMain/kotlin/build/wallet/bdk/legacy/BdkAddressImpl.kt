package build.wallet.bdk.legacy

import build.wallet.bdk.bindings.BdkAddress
import build.wallet.bdk.bindings.BdkNetwork
import build.wallet.bdk.bindings.BdkScript

internal class BdkAddressImpl(
  private val ffiAddress: FfiAddress,
) : BdkAddress {
  override fun asString(): String {
    return ffiAddress.asString()
  }

  override fun scriptPubkey(): BdkScript {
    return BdkScriptImpl(rawOutputScript = ffiAddress.scriptPubkey().toBytes())
  }

  override fun network(): BdkNetwork {
    return ffiAddress.network().bdkNetwork
  }

  override fun isValidForNetwork(network: BdkNetwork): Boolean {
    return ffiAddress.isValidForNetwork(network = network.ffiNetwork)
  }
}
