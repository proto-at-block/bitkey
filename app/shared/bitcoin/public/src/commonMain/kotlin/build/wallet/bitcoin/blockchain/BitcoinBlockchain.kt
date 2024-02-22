package build.wallet.bitcoin.blockchain

import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionDetail
import com.github.michaelbull.result.Result

interface BitcoinBlockchain {
  /**
   * Broadcasts a PSBT to the Bitcoin network.
   *
   * @return the broadcast time and the transaction.
   */
  suspend fun broadcast(psbt: Psbt): Result<TransactionDetail, BdkError>

  /**
   * Returns the latest block height.
   */
  suspend fun getLatestBlockHeight(): Result<Long, BdkError>

  /**
   * Returns the latest block hash.
   */
  suspend fun getLatestBlockHash(): Result<String, BdkError>
}
