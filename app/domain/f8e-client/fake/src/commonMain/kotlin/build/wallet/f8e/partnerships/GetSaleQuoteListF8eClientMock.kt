package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.compose.collections.immutableListOf
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import build.wallet.partnerships.SaleQuote
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetSaleQuoteListF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : GetSaleQuoteListF8eClient {
  val getSaleQuotesListCall = turbine("get sale quotes")

  private val successResult =
    Ok(
      GetSaleQuoteListF8eClient.Success(
        immutableListOf(
          SaleQuote(
            fiatCurrency = "USD",
            cryptoAmount = 0.123456,
            partnerInfo =
              PartnerInfo(
                name = "partner",
                logoUrl = "https://logo.url.example.com",
                partnerId = PartnerId("partner"),
                logoBadgedUrl = "https://badged-logo.url.example.com"
              ),
            fiatAmount = 12.34,
            userFeeFiat = 56.78
          )
        )
      )
    )

  private var quotesListResult: Result<GetSaleQuoteListF8eClient.Success, NetworkingError> = successResult

  override suspend fun getSaleQuotes(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    cryptoAmount: BitcoinMoney,
    fiatCurrency: FiatCurrency,
  ): Result<GetSaleQuoteListF8eClient.Success, NetworkingError> {
    getSaleQuotesListCall.add(Unit)
    return quotesListResult
  }
}
