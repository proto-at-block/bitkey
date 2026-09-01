package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.SaleOptions
import build.wallet.partnerships.SalePartnerLimits
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetSaleOptionsF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : GetSaleOptionsF8eClient {
  val getSaleOptionsCall = turbine("get sale options")

  var saleOptionsResult: Result<SaleOptions, NetworkingError> = Ok(SaleOptionsFake)

  override suspend fun saleOptions(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    currency: FiatCurrency,
  ): Result<SaleOptions, NetworkingError> {
    getSaleOptionsCall.add(Unit)
    return saleOptionsResult
  }
}

val SaleOptionsFake = SaleOptions(
  country = "US",
  fiatCurrency = "USD",
  minFiatAmount = 10.0,
  maxFiatAmount = 50_000.0,
  partnerLimits = listOf(
    SalePartnerLimits(
      partner = PartnerId("partner"),
      minFiatAmount = 10.0,
      maxFiatAmount = 50_000.0
    )
  )
)
