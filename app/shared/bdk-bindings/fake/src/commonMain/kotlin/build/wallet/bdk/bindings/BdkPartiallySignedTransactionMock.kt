package build.wallet.bdk.bindings

class BdkPartiallySignedTransactionMock(
  val txId: String = "",
  val extractTxResult: BdkTransaction = BdkTransactionMock(),
  val feeAmount: ULong? = null,
) : BdkPartiallySignedTransaction {
  override fun feeAmount(): ULong? = feeAmount

  override fun txid(): String = txId

  override fun serialize(): String = ""

  override fun extractTx(): BdkTransaction = extractTxResult
}
