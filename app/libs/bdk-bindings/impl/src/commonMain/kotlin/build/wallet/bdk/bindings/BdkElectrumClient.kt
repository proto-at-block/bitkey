package build.wallet.bdk.bindings

import uniffi.bdk.Transaction
import uniffi.bdk.Txid

/**
 * Minimal Electrum surface used by [BdkBlockchainImpl], extracted for testability.
 */
internal interface BdkElectrumClient {
  fun transactionBroadcast(transaction: Transaction): Txid

  fun latestBlockHeight(): Long

  fun blockHash(height: ULong): String

  fun estimateFee(target: ULong): Double

  fun transactionGet(txid: Txid): Transaction
}
