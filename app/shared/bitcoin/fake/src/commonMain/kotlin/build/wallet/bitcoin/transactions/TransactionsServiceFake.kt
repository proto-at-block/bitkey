package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.transactions.TransactionsData.LoadingTransactionsData
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.time.someInstant
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class TransactionsServiceFake : TransactionsService {
  val transactionsData = MutableStateFlow<TransactionsData>(LoadingTransactionsData)
  var spendingWallet = MutableStateFlow<SpendingWallet?>(null)

  override fun spendingWallet() = spendingWallet

  override suspend fun syncTransactions(): Result<Unit, Error> {
    spendingWallet.value?.sync()
    return Ok(Unit)
  }

  override fun transactionsData() = transactionsData

  val broadcastedPsbts = MutableStateFlow<List<Psbt>>(emptyList())
  var broadcastError: Error? = null

  override suspend fun broadcast(
    psbt: Psbt,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<BroadcastDetail, Error> {
    broadcastedPsbts.update { it + psbt }
    return broadcastError?.let { Err(it) } ?: Ok(
      BroadcastDetail(
        broadcastTime = someInstant,
        transactionId = "txid-fake"
      )
    )
  }

  fun reset() {
    transactionsData.value = LoadingTransactionsData
    broadcastError = null
    broadcastedPsbts.value = emptyList()
    spendingWallet.value = null
  }
}
