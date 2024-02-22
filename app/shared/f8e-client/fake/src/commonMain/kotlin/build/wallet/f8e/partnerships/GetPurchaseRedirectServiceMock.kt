package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitcoin.address.BitcoinAddress
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.f8e.partnerships.GetPurchaseRedirectService.Success
import build.wallet.f8e.partnerships.RedirectUrlType.WIDGET
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.FiatMoney
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetPurchaseRedirectServiceMock(
  turbine: (String) -> Turbine<Any>,
) : GetPurchaseRedirectService {
  val getPurchasePartnersRedirectCall = turbine("get purchase partners redirect")

  private val successResponse: Result<Success, NetworkingError> =
    Ok(Success(RedirectInfo(null, "http://example.com/redirect_url", WIDGET)))

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
