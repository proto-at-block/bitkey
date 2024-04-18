package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetTransferPartnerListService.Success
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetTransferPartnerListServiceMock(
  turbine: (String) -> Turbine<Any>,
) : GetTransferPartnerListService {
  val getTransferPartnersCall = turbine("get transfer partners calls")

  private val successResponse: Result<Success, NetworkingError> =
    Ok(
      Success(
        listOf(
          PartnerInfo("LogoUrl", "Partner 1", "Partner1"),
          PartnerInfo(null, "Partner 2", "Partner2")
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
