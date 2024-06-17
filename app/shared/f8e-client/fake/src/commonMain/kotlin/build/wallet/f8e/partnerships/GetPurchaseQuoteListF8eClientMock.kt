package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.FiatMoney
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetPurchaseQuoteListF8eClientMock(
  turbine: (String) -> Turbine<Any>,
) : GetPurchaseQuoteListF8eClient {
  val getPurchaseQuotesListCall = turbine("get purchase quotes")

  private val successResult =
    Ok(
      GetPurchaseQuoteListF8eClient.Success(
        listOf(
          Quote(
            fiatCurrency = "USD",
            cryptoAmount = 0.00195701,
            networkFeeCrypto = 0.0002710900770218228,
            networkFeeFiat = 11.87,
            cryptoPrice = 43786.18402563094,
            partnerInfo =
              PartnerInfo(
                name = "partner",
                logoUrl = "https://logo.url.example.com",
                partnerId = PartnerId("partner")
              ),
            userFeeFiat = 0.0,
            quoteId = "quoteId"
          )
        )
      )
    )

  private var quotesListResult: Result<GetPurchaseQuoteListF8eClient.Success, NetworkingError> = successResult

  override suspend fun purchaseQuotes(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    paymentMethod: String,
  ): Result<GetPurchaseQuoteListF8eClient.Success, NetworkingError> {
    getPurchaseQuotesListCall.add(Unit)
    return quotesListResult
  }

  fun reset() {
    quotesListResult = successResult
  }
}
