package build.wallet.partnerships

import app.cash.turbine.Turbine
import app.cash.turbine.plusAssign
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Duration

class PartnershipTransactionsServiceMock(
  val clearCalls: Turbine<Unit>,
  val syncCalls: Turbine<Unit>,
  val createCalls: Turbine<Pair<PartnerInfo, PartnershipTransactionType>>,
  val fetchMostRecentCalls: Turbine<PartnershipTransactionId>,
  val updateRecentTransactionStatusCalls: Turbine<UpdateRecentTransactionStatusArgs>,
  var clearResponse: Result<Unit, Error> = Ok(Unit),
  var createResponse: Result<PartnershipTransaction, Error> = Ok(FakePartnershipTransaction),
  var fetchMostRecentResult: Result<PartnershipTransaction?, Error> = Ok(null),
  var updateRecentTransactionStatusResponse: Result<PartnershipTransaction?, Error> = Ok(null),
  val getCalls: Turbine<PartnershipTransactionId>,
) : PartnershipTransactionsService {
  override val transactions = MutableStateFlow<List<PartnershipTransaction>>(emptyList())
  override val previouslyUsedPartnerIds = MutableStateFlow<List<PartnerId>>(emptyList())

  override suspend fun syncPendingTransactions(): Result<Unit, Error> {
    syncCalls.add(Unit)
    return Ok(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls.add(Unit)

    return clearResponse
  }

  override suspend fun create(
    id: PartnershipTransactionId,
    partnerInfo: PartnerInfo,
    type: PartnershipTransactionType,
  ): Result<PartnershipTransaction, Error> {
    createCalls.add(partnerInfo to type)

    return createResponse
  }

  override suspend fun updateRecentTransactionStatusIfExists(
    partnerId: PartnerId,
    status: PartnershipTransactionStatus,
    recency: Duration,
  ): Result<PartnershipTransaction?, Error> {
    updateRecentTransactionStatusCalls.add(UpdateRecentTransactionStatusArgs(partnerId, status, recency))

    return updateRecentTransactionStatusResponse
  }

  override suspend fun syncTransaction(
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> {
    fetchMostRecentCalls.add(transactionId)
    return fetchMostRecentResult
  }

  override suspend fun getTransactionById(
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> {
    getCalls += transactionId
    return Ok(transactions.value.find { it.id == transactionId })
  }

  fun reset() {
    clearResponse = Ok(Unit)
    createResponse = Ok(FakePartnershipTransaction)
    fetchMostRecentResult = Ok(null)
    updateRecentTransactionStatusResponse = Ok(null)
  }

  data class UpdateRecentTransactionStatusArgs(
    val partnerId: PartnerId,
    val status: PartnershipTransactionStatus,
    val recency: Duration,
  )
}
