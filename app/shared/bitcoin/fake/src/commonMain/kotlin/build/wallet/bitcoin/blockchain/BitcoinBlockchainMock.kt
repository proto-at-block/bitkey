package build.wallet.bitcoin.blockchain

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.bdk.bindings.BdkError
import build.wallet.bitcoin.transactions.Psbt
import build.wallet.bitcoin.transactions.TransactionDetail
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Clock

class BitcoinBlockchainMock(
  turbine: (String) -> Turbine<Any>,
  private val defaultBlockHeight: Long = 807770,
  private val defaultBlockHash: String = "00000000000000000000cb4e25f60ca18a883d449b723771d8ebf4d4dae1f07e",
  private val defaultTransactionDetail: TransactionDetail =
    TransactionDetail(
      broadcastTime = Clock.System.now(),
      transactionId = "abcdef"
    ),
) : BitcoinBlockchain {
  val broadcastCalls = turbine("broadcast psbt calls")
  var broadcastResult: Result<TransactionDetail, BdkError> = Ok(defaultTransactionDetail)

  override suspend fun broadcast(psbt: Psbt): Result<TransactionDetail, BdkError> {
    broadcastCalls += psbt
    return broadcastResult
  }

  var latestBlockHeight: Result<Long, BdkError> = Ok(defaultBlockHeight)

  override suspend fun getLatestBlockHeight(): Result<Long, BdkError> {
    return latestBlockHeight
  }

  var latestBlockHash: Result<String, BdkError> = Ok(defaultBlockHash)

  override suspend fun getLatestBlockHash(): Result<String, BdkError> {
    return latestBlockHash
  }

  fun reset() {
    latestBlockHeight = Ok(defaultBlockHeight)
    latestBlockHash = Ok(defaultBlockHash)
    broadcastResult = Ok(defaultTransactionDetail)
  }
}
