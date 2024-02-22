package build.wallet.bdk.bindings

interface BdkAddressBuilder {
  /**
   * Builds a [BdkAddress] using BDK.
   *
   * @param address - address to parse as a string.
   * @return If valid script format, returns [BdkAddress], else it will return [BdkError.Generic].
   */
  fun build(
    address: String,
    bdkNetwork: BdkNetwork,
  ): BdkResult<BdkAddress>

  /**
   * Assembles a [BdkAddress] with an output script and network.
   *
   * @param script - output script
   * @param network - network
   * @return If valid script and matches the specified network, return [BdkAddress], else returns [BdkError.Generic]
   */
  fun build(
    script: BdkScript,
    network: BdkNetwork,
  ): BdkResult<BdkAddress>
}
