package build.wallet.partnerships

import build.wallet.money.FiatMoney
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result

class PartnershipPurchaseServiceFake : PartnershipPurchaseService {
  var suggestedPurchaseAmounts: Result<SuggestedPurchaseAmounts, Error> =
    Ok(SuggestedPurchaseAmountsFake)

  override suspend fun getSuggestedPurchaseAmounts(): Result<SuggestedPurchaseAmounts, Error> {
    return suggestedPurchaseAmounts
  }

  private val defaultQuotes = listOf(
    PurchaseQuote(
      fiatCurrency = "USD",
      cryptoAmount = 0.00195701,
      networkFeeCrypto = 0.0002710900770218228,
      networkFeeFiat = 11.87,
      cryptoPrice = 43786.18402563094,
      partnerInfo = PartnerInfo(
        name = "partner",
        logoUrl = "https://logo.url.example.com",
        partnerId = PartnerId("partner"),
        logoBadgedUrl = "https://badged-logo.url.example.com"
      ),
      userFeeFiat = 0.0,
      quoteId = "quoteId"
    )
  )

  var purchaseQuotes: Result<List<PurchaseQuote>, Error> = Ok(defaultQuotes)

  override suspend fun loadPurchaseQuotes(amount: FiatMoney): Result<List<PurchaseQuote>, Error> {
    return purchaseQuotes
  }

  override suspend fun preparePurchase(
    quote: PurchaseQuote,
    purchaseAmount: FiatMoney,
  ): Result<PurchaseRedirectInfo, Throwable> {
    return Ok(
      PurchaseRedirectInfo(
        redirectMethod = PartnerRedirectionMethod.Web(
          urlString = "https://fake-partner.com/purchase",
          partnerInfo = PartnerInfoFake
        ),
        transaction = PartnershipTransactionFake
      )
    )
  }

  fun reset() {
    suggestedPurchaseAmounts = Ok(SuggestedPurchaseAmountsFake)
    purchaseQuotes = Ok(defaultQuotes)
    suggestedPurchaseAmounts = Ok(SuggestedPurchaseAmountsFake)
  }
}
