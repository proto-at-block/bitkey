package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetPurchaseRedirectF8eClient.Success
import build.wallet.f8e.partnerships.RedirectUrlType.WIDGET
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.FiatMoney
import build.wallet.partnerships.PartnershipTransactionId
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetPurchaseRedirectF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : GetPurchaseRedirectF8eClient {
  val getPurchasePartnersRedirectCall = turbine("get purchase partners redirect")

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

  private var purchaseRedirectResult: Result<Success, NetworkingError> = successResponse

  override suspend fun purchaseRedirect(
    fullAccountId: FullAccountId,
    address: BitcoinAddress,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    partner: String,
    paymentMethod: String,
    quoteId: String?,
  ): Result<Success, NetworkingError> {
    getPurchasePartnersRedirectCall.add(Unit)
    return purchaseRedirectResult
  }

  fun reset() {
    purchaseRedirectResult = successResponse
  }
}
