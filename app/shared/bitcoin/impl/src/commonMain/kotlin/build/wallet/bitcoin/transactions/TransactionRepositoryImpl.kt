package build.wallet.bitcoin.transactions

import build.wallet.db.DbError
import build.wallet.logging.logFailure
import build.wallet.money.exchange.ExchangeRateDao
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class TransactionRepositoryImpl(
  private val transactionDetailDao: TransactionDetailDao,
  private val exchangeRateDao: ExchangeRateDao,
) : TransactionRepository {
  override suspend fun setTransaction(transaction: Transaction): Result<Unit, DbError> =
    binding {
      transactionDetailDao.insert(
        transaction.transactionDetail.broadcastTime,
        transaction.transactionDetail.transactionId,
        transaction.estimatedConfirmationTime
      ).bind()

      when (val rates = transaction.exchangeRates) {
        null -> Unit
        else ->
          rates.forEach {
            exchangeRateDao.storeHistoricalExchangeRate(
              it,
              transaction.transactionDetail.broadcastTime
            )
              .bind()
          }
      }
    }.logFailure { "Unable to persist transaction and its exchange rates." }
}
