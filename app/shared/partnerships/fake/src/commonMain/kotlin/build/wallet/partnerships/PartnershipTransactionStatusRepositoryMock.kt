package build.wallet.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PartnershipTransactionStatusRepositoryMock(
  val clearCalls: Turbine<Unit>,
  val syncCalls: Turbine<Unit>,
  val createCalls: Turbine<Pair<PartnerInfo, PartnershipTransactionType>>,
  val fetchMostRecentCalls: Turbine<PartnershipTransactionId>,
  var clearResponse: Result<Unit, Error> = Ok(Unit),
  var createResponse: Result<PartnershipTransaction, Error> = Ok(FakePartnershipTransaction),
  var fetchMostRecentResult: Result<PartnershipTransaction?, Error> = Ok(null),
) : PartnershipTransactionsStatusRepository {
  override val transactions: Flow<List<PartnershipTransaction>> = flow {}

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

  override suspend fun syncTransaction(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    transactionId: PartnershipTransactionId,
  ): Result<PartnershipTransaction?, Error> {
    fetchMostRecentCalls.add(transactionId)
    return fetchMostRecentResult
  }
}
