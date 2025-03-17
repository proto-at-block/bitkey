package build.wallet.bdk.bindings

class BdkAddressMock(
  var address: String = "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs",
) : BdkAddress {
  override fun asString(): String = address

  override fun scriptPubkey(): BdkScript {
    return BdkScriptMock()
  }

  var network = BdkNetwork.BITCOIN

  override fun network(): BdkNetwork {
    return network
  }

  override fun isValidForNetwork(network: BdkNetwork): Boolean {
    return network == this.network
  }

  fun reset() {
    address = "bc1zw508d6qejxtdg4y5r3zarvaryvaxxpcs"
    network = BdkNetwork.BITCOIN
  }
}
