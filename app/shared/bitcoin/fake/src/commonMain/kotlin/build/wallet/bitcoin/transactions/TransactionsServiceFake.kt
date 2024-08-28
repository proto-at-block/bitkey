package build.wallet.bitcoin.transactions

import build.wallet.bitcoin.transactions.TransactionsData.LoadingTransactionsData
import build.wallet.bitcoin.wallet.SpendingWallet
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow

class TransactionsServiceFake : TransactionsService {
  val transactionsData = MutableStateFlow<TransactionsData>(LoadingTransactionsData)
  var spendingWallet = MutableStateFlow<SpendingWallet?>(null)

  override fun spendingWallet() = spendingWallet

  override suspend fun syncTransactions(): Result<Unit, Error> {
    spendingWallet.value?.sync()
    return Ok(Unit)
  }

  override fun transactionsData() = transactionsData

  fun reset() {
    transactionsData.value = LoadingTransactionsData
    spendingWallet.value = null
  }
}
