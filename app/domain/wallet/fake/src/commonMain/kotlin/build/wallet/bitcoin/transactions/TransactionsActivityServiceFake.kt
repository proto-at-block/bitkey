package build.wallet.bitcoin.transactions

import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import build.wallet.activity.TransactionsActivityState
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.mapNotNull

class TransactionsActivityServiceFake : TransactionsActivityService {
  override suspend fun sync(): Result<Unit, Error> = Ok(Unit)

  private val _transactionsState = MutableStateFlow<TransactionsActivityState>(
    TransactionsActivityState.InitialLoading
  )
  override val transactionsState: MutableStateFlow<TransactionsActivityState> = _transactionsState

  @Suppress("DEPRECATION")
  override val transactions = MutableStateFlow<List<Transaction>?>(null)

  override val activeAndInactiveWalletTransactions = MutableStateFlow<List<Transaction>?>(null)

  override fun transactionById(transactionId: String): Flow<Transaction?> {
    return transactions.mapNotNull { txns ->
      txns?.find { it.id == transactionId }
    }
  }

  fun reset() {
    transactions.value = null
    activeAndInactiveWalletTransactions.value = null
    _transactionsState.value = TransactionsActivityState.InitialLoading
  }
}
