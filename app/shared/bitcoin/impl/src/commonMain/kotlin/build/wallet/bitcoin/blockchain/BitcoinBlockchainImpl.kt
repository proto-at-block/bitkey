package build.wallet.bitcoin.blockchain

import build.wallet.bdk.bindings.*
import build.wallet.bitcoin.bdk.BdkBlockchainProvider
import build.wallet.bitcoin.transactions.BitcoinTransactionId
import build.wallet.bitcoin.transactions.BroadcastDetail
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.logging.*
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.coroutineBinding
import kotlinx.datetime.Clock

class BitcoinBlockchainImpl(
  private val bdkBlockchainProvider: BdkBlockchainProvider,
  private val bdkPsbtBuilder: BdkPartiallySignedTransactionBuilder,
  private val clock: Clock,
) : BitcoinBlockchain {
  override suspend fun broadcast(psbt: Psbt): Result<BroadcastDetail, BdkError> =
    coroutineBinding {
      val bdkPsbt =
        bdkPsbtBuilder.build(psbtBase64 = psbt.base64)
          .result
          .bind()

      logDebug { "Attempting to broadcast psbt: ${psbt.base64}" }
      val blockchain =
        bdkBlockchainProvider.blockchain()
          .result
          .bind()

      blockchain.broadcast(bdkPsbt.extractTx()).result.bind()
      logDebug { "Successfully broadcast psbt" }

      BroadcastDetail(
        broadcastTime = clock.now(),
        transactionId = bdkPsbt.txid()
      )
    }

  override suspend fun getLatestBlockHeight(): Result<Long, BdkError> =
    coroutineBinding {
      val blockchain = bdkBlockchainProvider.blockchain().result.bind()
      blockchain.getHeight().result.bind()
    }

  override suspend fun getLatestBlockHash(): Result<String, BdkError> =
    coroutineBinding {
      val blockchain = bdkBlockchainProvider.blockchain().result.bind()
      val blockHeight = blockchain.getHeight().result.bind()
      blockchain.getBlockHash(blockHeight).result.bind()
    }

  override suspend fun getTx(txid: BitcoinTransactionId): Result<BdkTransaction?, BdkError> =
    coroutineBinding {
      val blockchain = bdkBlockchainProvider.blockchain().result.bind()
      blockchain.getTx(txid.value).result.bind()
    }
}
