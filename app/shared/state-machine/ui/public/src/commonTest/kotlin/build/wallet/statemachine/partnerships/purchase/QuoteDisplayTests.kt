package build.wallet.statemachine.partnerships.purchase

import build.wallet.f8e.partnerships.Quote
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRateFake
import build.wallet.money.formatter.MoneyDisplayFormatterFake
import build.wallet.partnerships.PartnerId
import build.wallet.partnerships.PartnerInfo
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe

class QuoteDisplayTests : FunSpec({
  test("valid quote is properly mapped") {
    val quote = Quote(
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

    val quoteDisplay = quote.toQuoteDisplay(
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      exchangeRates = listOf(
        ExchangeRateFake
      ),
      currencyConverter = CurrencyConverterFake()
    )

    quoteDisplay.quote.shouldBe(quote)
    quoteDisplay.bitcoinDisplayAmount.shouldBe("195,701 sats")
    quoteDisplay.fiatDisplayAmount.shouldBe("$0.01")
  }

  test("a quote with unsupported currency has null fiat value") {
    val quote = Quote(
      fiatCurrency = "Neopoints",
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

    val quoteDisplay = quote.toQuoteDisplay(
      moneyDisplayFormatter = MoneyDisplayFormatterFake,
      exchangeRates = listOf(
        ExchangeRateFake
      ),
      currencyConverter = CurrencyConverterFake()
    )

    quoteDisplay.quote.shouldBe(quote)
    quoteDisplay.bitcoinDisplayAmount.shouldBe("195,701 sats")
    quoteDisplay.fiatDisplayAmount.shouldBeNull()
  }
})
