package build.wallet.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

class PartnershipTransactionStatusRepositoryMock(
  val clearCalls: Turbine<Unit>,
  val syncCalls: Turbine<Unit>,
  val createCalls: Turbine<Pair<PartnerInfo, PartnershipTransactionType>>,
  val fetchMostRecentCalls: Turbine<PartnershipTransactionId>,
  val updateRecentTransactionStatusCalls: Turbine<UpdateRecentTransactionStatusArgs>,
  var clearResponse: Result<Unit, Error> = Ok(Unit),
  var createResponse: Result<PartnershipTransaction, Error> = Ok(FakePartnershipTransaction),
  var fetchMostRecentResult: Result<PartnershipTransaction?, Error> = Ok(null),
  var updateRecentTransactionStatusResponse: Result<PartnershipTransaction?, Error> = Ok(null),
) : PartnershipTransactionsStatusRepository {
  override val transactions: Flow<List<PartnershipTransaction>> = flow {}
  override val previouslyUsedPartnerIds = MutableStateFlow<List<PartnerId>>(emptyList())

  override suspend fun sync() {
    syncCalls.add(Unit)
  }

  override suspend fun clear(): Result<Unit, Error> {
    clearCalls.add(Unit)

    return clearResponse
  }

  override suspend fun create(
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
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> {
    fetchMostRecentCalls.add(transactionId)
    return fetchMostRecentResult
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
