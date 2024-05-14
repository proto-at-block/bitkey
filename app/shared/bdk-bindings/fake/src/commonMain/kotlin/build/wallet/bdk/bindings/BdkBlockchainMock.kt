package build.wallet.bdk.bindings

data class BdkBlockchainMock(
  var blockHeightResult: BdkResult<Long>,
  var blockHashResult: BdkResult<String>,
  var broadcastResult: BdkResult<Unit>,
  var feeRateResult: BdkResult<Float>,
) : BdkBlockchain {
  override fun broadcastBlocking(transaction: BdkTransaction) = broadcastResult

  override fun getHeightBlocking(): BdkResult<Long> = blockHeightResult

  override fun getBlockHashBlocking(height: Long): BdkResult<String> = blockHashResult

  override fun estimateFeeBlocking(targetBlocks: ULong): BdkResult<Float> = feeRateResult
}
