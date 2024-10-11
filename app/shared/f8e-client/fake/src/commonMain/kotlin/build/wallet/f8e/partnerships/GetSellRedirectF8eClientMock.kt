package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetSellRedirectF8eClient.Success
import build.wallet.f8e.partnerships.RedirectUrlType.WIDGET
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.FiatMoney
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetSellRedirectF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : GetSellRedirectF8eClient {
  val getSellPartnersRedirectCall = turbine("get sell partners redirect")

  private val successResponse: Result<Success, NetworkingError> =
    Ok(
      Success(
        RedirectInfo(
          appRestrictions = null,
          url = "http://example.com/redirect_url",
          redirectType = WIDGET,
          partnerTransactionId = PartnershipTransactionId("some-partner-transaction-id")
        )
      )
    )

  private var sellRedirectResult: Result<Success, NetworkingError> = successResponse

  override suspend fun sellRedirect(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    partner: String,
  ): Result<Success, NetworkingError> {
    getSellPartnersRedirectCall.add(Unit)
    return sellRedirectResult
  }
}
