package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetPartnerF8eClientMock(
  turbine: (String) -> Turbine<PartnerId>,
  var response: Result<PartnerInfo, NetworkingError> = Ok(
    PartnerInfo("LogoUrl", "Partner 1", PartnerId("Partner1"))
  ),
) : GetPartnerF8eClient {
  val getPartnerCalls = turbine("get partner")

  override suspend fun getPartner(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    partner: PartnerId,
  ): Result<PartnerInfo, NetworkingError> {
    getPartnerCalls.add(partner)

    return response
  }
}
