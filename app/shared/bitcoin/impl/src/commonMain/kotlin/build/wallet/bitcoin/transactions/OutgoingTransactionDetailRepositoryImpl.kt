package build.wallet.bitcoin.transactions

import build.wallet.db.DbError
import build.wallet.logging.logFailure
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.coroutines.binding.binding

class OutgoingTransactionDetailRepositoryImpl(
  private val outgoingTransactionDetailDao: OutgoingTransactionDetailDao,
) : OutgoingTransactionDetailRepository {
  override suspend fun persistDetails(details: OutgoingTransactionDetail): Result<Unit, DbError> =
    binding {
      outgoingTransactionDetailDao.insert(
        details.broadcastDetail.broadcastTime,
        details.broadcastDetail.transactionId,
        details.estimatedConfirmationTime,
        details.exchangeRates
      ).bind()
    }.logFailure { "Unable to persist transaction and its exchange rates." }
}
