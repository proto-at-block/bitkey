package build.wallet.bitcoin.blockchain

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkTransaction
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitcoin.transactions.BroadcastDetail
import build.wallet.bitcoin.transactions.Psbt
import com.github.michaelbull.result.Result

interface BitcoinBlockchain {
  /**
   * Broadcasts a PSBT to the Bitcoin network.
   *
   * @return the broadcast time and the transaction.
   */
  suspend fun broadcast(psbt: Psbt): Result<BroadcastDetail, BdkError>

  /**
   * Returns the latest block height.
   */
  suspend fun getLatestBlockHeight(): Result<Long, BdkError>

  /**
   * Returns the latest block hash.
   */
  suspend fun getLatestBlockHash(): Result<String, BdkError>

  /**
   * Returns transaction information given a txid.
   */
  suspend fun getTx(txid: BitcoinTransactionId): Result<BdkTransaction?, BdkError>
}
