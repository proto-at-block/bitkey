package build.wallet.f8e.partnerships

import app.cash.turbine.Turbine
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.FiatMoney
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class GetPurchaseQuoteListServiceServiceMock(
  turbine: (String) -> Turbine<Any>,
) : GetPurchaseQuoteListService {
  val getPurchaseQuotesListServiceCall = turbine("get purchase quotes")

  private val successResult =
    Ok(
      GetPurchaseQuoteListService.Success(
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
                partner = "partner"
              ),
            userFeeFiat = 0.0,
            quoteId = "quoteId"
          )
        )
      )
    )

  private var quotesListResult: Result<GetPurchaseQuoteListService.Success, NetworkingError> = successResult

  override suspend fun purchaseQuotes(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    paymentMethod: String,
  ): Result<GetPurchaseQuoteListService.Success, NetworkingError> {
    getPurchaseQuotesListServiceCall.add(Unit)
    return quotesListResult
  }

  fun reset() {
    quotesListResult = successResult
  }
}
