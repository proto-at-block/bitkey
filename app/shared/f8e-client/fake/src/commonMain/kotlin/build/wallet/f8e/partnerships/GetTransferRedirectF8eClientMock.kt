package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetTransferRedirectF8eClient.Success
import build.wallet.f8e.partnerships.RedirectUrlType.WIDGET
import build.wallet.ktor.result.NetworkingError
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetTransferRedirectF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : GetTransferRedirectF8eClient {
  val getTransferPartnersRedirectCall = turbine("get transfer partners redirect calls")

  private val successResponse: Result<Success, NetworkingError> =
    Ok(Success(RedirectInfo(null, "http://example.com/redirect_url", WIDGET)))

  var transferRedirectResult: Result<Success, NetworkingError> = successResponse

  override suspend fun getTransferRedirect(
    fullAccountId: FullAccountId,
    address: BitcoinAddress,
    f8eEnvironment: F8eEnvironment,
    partner: String,
    partnerTransactionId: PartnershipTransactionId?,
  ): Result<Success, NetworkingError> {
    getTransferPartnersRedirectCall.add(Unit)
    return transferRedirectResult
  }

  fun reset() {
    transferRedirectResult = successResponse
  }
}
