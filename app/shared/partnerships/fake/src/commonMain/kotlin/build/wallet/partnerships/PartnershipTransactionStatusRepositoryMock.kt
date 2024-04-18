package build.wallet.partnerships

import app.cash.turbine.Turbine
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class PartnershipTransactionStatusRepositoryMock(
  val clearCalls: Turbine<Unit>,
  val syncCalls: Turbine<Unit>,
  val createCalls: Turbine<Pair<PartnerInfo, PartnershipTransactionType>>,
  val clearResponse: Result<Unit, Error> = Ok(Unit),
  val createResponse: Result<PartnershipTransaction, Error> = Ok(FakePartnershipTransaction),
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
}
