package build.wallet.bdk.bindings

class BdkBlockchainFactoryMock(
  var blockchainResult: BdkResult<BdkBlockchain>,
) : BdkBlockchainFactory {
  override fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> =
    blockchainResult

  fun reset(result: BdkResult<BdkBlockchain>) {
    blockchainResult = result
  }
}
