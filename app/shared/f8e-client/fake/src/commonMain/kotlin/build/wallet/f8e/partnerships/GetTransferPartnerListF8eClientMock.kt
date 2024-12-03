package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetTransferPartnerListF8eClient.Success
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetTransferPartnerListF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : GetTransferPartnerListF8eClient {
  val getTransferPartnersCall = turbine("get transfer partners calls")

  private val successResponse: Result<Success, NetworkingError> =
    Ok(
      Success(
        listOf(
          PartnerInfo("LogoUrl", "LogoBadgedUrl", "Partner 1", PartnerId("Partner1")),
          PartnerInfo(null, null, "Partner 2", PartnerId("Partner2"))
        )
      )
    )

  var partnersResult: Result<Success, NetworkingError> = successResponse

  override suspend fun getTransferPartners(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
  ): Result<Success, NetworkingError> {
    getTransferPartnersCall.add(Unit)
    return partnersResult
  }

  fun reset() {
    partnersResult = successResponse
  }
}
