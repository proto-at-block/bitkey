package build.wallet.bdk.bindings

class BdkTransactionMock(
  private val output: List<BdkTxOut> = emptyList(),
) : BdkTransaction {
  override fun txid(): String = ""

  override fun serialize(): List<UByte> = emptyList()

  override fun weight(): ULong = ULong.MAX_VALUE

  override fun size(): ULong = ULong.MAX_VALUE

  override fun vsize(): ULong = ULong.MAX_VALUE

  override fun input(): List<BdkTxIn> = emptyList()

  override fun output(): List<BdkTxOut> = output
}
