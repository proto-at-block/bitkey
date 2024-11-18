package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.AccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnershipTransactionId
import build.wallet.partnerships.PartnershipTransactionType
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetPartnershipTransactionF8eClientMock(
  turbine: (String) -> Turbine<Pair<PartnerId, PartnershipTransactionId>>,
  var response: Result<F8ePartnershipTransaction, NetworkingError> = Ok(FakePartnershipTransfer),
) : GetPartnershipTransactionF8eClient {
  val getTransactionCalls = turbine("get partnership transaction")

  override suspend fun getPartnershipTransaction(
    accountId: AccountId,
    f8eEnvironment: F8eEnvironment,
    partner: PartnerId,
    partnershipTransactionId: PartnershipTransactionId,
    transactionType: PartnershipTransactionType,
  ): Result<F8ePartnershipTransaction, NetworkingError> {
    getTransactionCalls.add(partner to partnershipTransactionId)

    return response
  }
}
