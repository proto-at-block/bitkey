package build.wallet.bitcoin.transactions

import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull

class TransactionsActivityServiceFake : TransactionsActivityService {
  override suspend fun sync(): Result<Unit, Error> = Ok(Unit)

  override val transactions = MutableStateFlow<List<Transaction>>(emptyList())

  override val activeAndInactiveWalletTransactions = MutableStateFlow<List<Transaction>>(emptyList())

  override fun transactionById(transactionId: String): Flow<Transaction?> {
    return transactions.mapNotNull {
      it.find { it.id == transactionId }
    }
  }

  fun reset() {
    transactions.value = emptyList()
    activeAndInactiveWalletTransactions.value = emptyList()
  }
}
