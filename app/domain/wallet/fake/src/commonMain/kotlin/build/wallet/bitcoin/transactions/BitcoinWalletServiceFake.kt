package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.time.someInstant
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class BitcoinWalletServiceFake : BitcoinWalletService {
  val transactionsData = MutableStateFlow<TransactionsData?>(null)
  var spendingWallet = MutableStateFlow<SpendingWallet?>(null)

  override fun spendingWallet() = spendingWallet

  override suspend fun sync(): Result<Unit, Error> {
    spendingWallet.value?.sync()
    return Ok(Unit)
  }

  override fun transactionsData() = transactionsData

  fun setTransactions(transactions: List<BitcoinTransaction>) {
    transactionsData.update {
      (it ?: TransactionsDataMock).copy(
        transactions = transactions.toImmutableList()
      )
    }
  }

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

  var createPsbtsForSendAmountResult: Result<PsbtsForSendAmount, Error>? = null

  override suspend fun createPsbtsForSendAmount(
    sendAmount: BitcoinTransactionSendAmount,
    recipientAddress: BitcoinAddress,
  ): Result<PsbtsForSendAmount, Error> {
    return createPsbtsForSendAmountResult ?: Ok(
      PsbtsForSendAmount(
        fastest = PsbtMock,
        thirtyMinutes = PsbtMock,
        sixtyMinutes = PsbtMock
      )
    )
  }

  fun reset() {
    transactionsData.value = null
    broadcastError = null
    broadcastedPsbts.value = emptyList()
    spendingWallet.value = null
    createPsbtsForSendAmountResult = null
  }
}
