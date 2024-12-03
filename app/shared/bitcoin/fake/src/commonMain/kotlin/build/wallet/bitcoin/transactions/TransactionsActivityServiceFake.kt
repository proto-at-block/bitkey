package build.wallet.bitcoin.transactions

import build.wallet.activity.Transaction
import build.wallet.activity.TransactionsActivityService
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class TransactionsActivityServiceFake : TransactionsActivityService {
  override suspend fun sync(): Result<Unit, Error> = Ok(Unit)

  override val transactions = MutableStateFlow<List<Transaction>>(emptyList())

  fun reset() {
    transactions.value = emptyList()
  }
}
