package build.wallet.bitcoin.blockchain

import build.wallet.bdk.bindings.BdkError
import build.wallet.bdk.bindings.BdkPartiallySignedTransactionBuilder
import build.wallet.bdk.bindings.broadcast
import build.wallet.bdk.bindings.getBlockHash
import build.wallet.bdk.bindings.getHeight
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.transactions.BroadcastDetail
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.logging.LogLevel.Debug
import build.wallet.logging.log
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding
import kotlinx.datetime.Clock

class BitcoinBlockchainImpl(
  private val bdkBlockchainProvider: BdkBlockchainProvider,
  private val bdkPsbtBuilder: BdkPartiallySignedTransactionBuilder,
  private val clock: Clock,
) : BitcoinBlockchain {
  override suspend fun broadcast(psbt: Psbt): Result<BroadcastDetail, BdkError> =
    binding {
      val bdkPsbt =
        bdkPsbtBuilder.build(psbtBase64 = psbt.base64)
          .result
          .bind()

      log(Debug) { "Attempting to broadcast psbt: ${psbt.base64}" }
      val blockchain =
        bdkBlockchainProvider.blockchain()
          .result
          .bind()

      blockchain.broadcast(bdkPsbt.extractTx()).result.bind()
      log { "Successfully broadcast psbt" }

      BroadcastDetail(
        broadcastTime = clock.now(),
        transactionId = bdkPsbt.txid()
      )
    }

  override suspend fun getLatestBlockHeight(): Result<Long, BdkError> =
    binding {
      val blockchain = bdkBlockchainProvider.blockchain().result.bind()
      blockchain.getHeight().result.bind()
    }

  override suspend fun getLatestBlockHash(): Result<String, BdkError> =
    binding {
      val blockchain = bdkBlockchainProvider.blockchain().result.bind()
      val blockHeight = blockchain.getHeight().result.bind()
      blockchain.getBlockHash(blockHeight).result.bind()
    }
}
