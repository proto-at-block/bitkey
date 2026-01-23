package build.wallet.bdk.bindings

class LegacyBdkBlockchainFactoryMock(
  var blockchainResult: BdkResult<BdkBlockchain>,
) : LegacyBdkBlockchainFactory {
  override fun blockchainBlocking(config: BdkBlockchainConfig): BdkResult<BdkBlockchain> =
    blockchainResult

  fun reset(result: BdkResult<BdkBlockchain>) {
    blockchainResult = result
  }
}
