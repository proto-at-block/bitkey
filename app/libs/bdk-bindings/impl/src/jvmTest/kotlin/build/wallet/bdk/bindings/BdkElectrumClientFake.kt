package build.wallet.bdk.bindings

import uniffi.bdk.Transaction
import uniffi.bdk.Txid

/**
 * Lightweight fake for [BdkElectrumClient] used in unit tests.
 *
 * Behaviors are configurable via public vars and maps so tests can
 * exercise success and failure paths without touching real BDK I/O.
 */
class BdkElectrumClientFake : BdkElectrumClient {
  val broadcastedTransactions = mutableListOf<Transaction>()

  var broadcastResult: Txid = Txid.fromString(DEFAULT_TXID)
  var broadcastError: Throwable? = null

  var latestBlockHeightResult: Long = 0
  var latestBlockHeightError: Throwable? = null

  val blockHashes: MutableMap<ULong, String> = mutableMapOf()
  var blockHashError: Throwable? = null

  var estimateFeeResult: Double = 0.0
  var estimateFeeError: Throwable? = null

  val transactions: MutableMap<String, Transaction> = mutableMapOf()
  var transactionGetError: Throwable? = null

  override fun transactionBroadcast(transaction: Transaction): Txid {
    broadcastedTransactions += transaction
    broadcastError?.let { throw it }
    return broadcastResult
  }

  override fun latestBlockHeight(): Long {
    latestBlockHeightError?.let { throw it }
    return latestBlockHeightResult
  }

  override fun blockHash(height: ULong): String {
    blockHashError?.let { throw it }
    return blockHashes[height] ?: error("Block hash for height $height not stubbed")
  }

  override fun estimateFee(target: ULong): Double {
    estimateFeeError?.let { throw it }
    return estimateFeeResult
  }

  override fun transactionGet(txid: Txid): Transaction {
    transactionGetError?.let { throw it }
    return transactions[txid.toString()]
      ?: throw NoSuchElementException("Transaction not stubbed for txid $txid")
  }

  companion object {
    const val DEFAULT_TXID: String =
      "4a5e1e4baab89f3a32518a88c31bc87f618f76673e2cc77ab2127b7afdeda33b"
  }
}
