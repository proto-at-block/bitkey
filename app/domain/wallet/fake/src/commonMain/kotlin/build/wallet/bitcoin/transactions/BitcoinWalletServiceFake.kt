package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitcoin.wallet.SpendingWallet
import build.wallet.time.someInstant
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.onSuccess
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class BitcoinWalletServiceFake : BitcoinWalletService {
  private val defaultBroadcastTransactionId: (Psbt) -> String = { it.id }

  val transactionsData = MutableStateFlow<TransactionsData?>(null)
  var spendingWallet = MutableStateFlow<SpendingWallet?>(null)
  var syncResult: Result<Unit, Error> = Ok(Unit)

  override fun spendingWallet() = spendingWallet

  override suspend fun sync(): Result<Unit, Error> {
    return syncResult.onSuccess {
      spendingWallet.value?.sync()
    }
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
  var broadcastTransactionId: (Psbt) -> String = defaultBroadcastTransactionId

  override suspend fun broadcast(
    psbt: Psbt,
    estimatedTransactionPriority: EstimatedTransactionPriority,
  ): Result<BroadcastDetail, Error> {
    broadcastedPsbts.update { it + psbt }
    return broadcastError?.let { Err(it) } ?: Ok(
      BroadcastDetail(
        broadcastTime = someInstant,
        transactionId = broadcastTransactionId(psbt)
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
    broadcastTransactionId = defaultBroadcastTransactionId
    broadcastedPsbts.value = emptyList()
    spendingWallet.value = null
    syncResult = Ok(Unit)
    createPsbtsForSendAmountResult = null
  }
}
