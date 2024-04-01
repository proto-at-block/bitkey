package build.wallet.bitcoin.transactions

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import build.wallet.db.DbError
import build.wallet.money.exchange.ExchangeRate
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.datetime.Instant

class OutgoingTransactionDetailDaoMock(
  turbine: (String) -> Turbine<Any>,
) : OutgoingTransactionDetailDao {
  private val internalMutableMap: MutableMap<String, TransactionInfo> = mutableMapOf()
  val clearCalls = turbine("clear transaction calls")
  val insertCalls = turbine("insert transaction calls")

  override suspend fun insert(
    broadcastTime: Instant,
    transactionId: String,
    estimatedConfirmationTime: Instant,
    exchangeRates: List<ExchangeRate>?,
  ): Result<Unit, DbError> {
    insertCalls += Pair(broadcastTime, transactionId)
    internalMutableMap[transactionId] =
      TransactionInfo(
        broadcastTime = broadcastTime,
        confirmationTime = estimatedConfirmationTime
      )
    return Ok(Unit)
  }

  override suspend fun broadcastTimeForTransaction(transactionId: String): Instant? {
    return internalMutableMap[transactionId]?.broadcastTime
  }

  override suspend fun confirmationTimeForTransaction(transactionId: String): Instant? {
    return internalMutableMap[transactionId]?.confirmationTime
  }

  override suspend fun clear(): Result<Unit, DbError> {
    clearCalls += Unit
    reset()
    return Ok(Unit)
  }

  fun reset() {
    internalMutableMap.clear()
  }

  private data class TransactionInfo(
    val broadcastTime: Instant,
    val confirmationTime: Instant,
  )
}
