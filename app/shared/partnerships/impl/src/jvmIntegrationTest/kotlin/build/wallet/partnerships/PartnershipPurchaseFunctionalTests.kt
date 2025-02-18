package build.wallet.partnerships

import app.cash.turbine.test
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.money.FiatMoney.Companion.eur
import build.wallet.money.FiatMoney.Companion.usd
import build.wallet.partnerships.PartnerRedirectionMethod.Web
import build.wallet.partnerships.PartnershipTransactionType.PURCHASE
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.shouldBeErrOfType
import build.wallet.testing.shouldBeOk
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class PartnershipPurchaseFunctionalTests : FunSpec({
  test("error loading purchase quotes without account") {
    val app = launchNewApp()
    app.partnershipPurchaseService.loadPurchaseQuotes(usd(10.00)).shouldBeErrOfType<Error>()
      .message.shouldBe("Expected active full account.")
  }

  test("load purchase quotes - returns signet faucet partner") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    app.partnershipPurchaseService.loadPurchaseQuotes(eur(10.00)).shouldBeOk()
      .single()
      .run {
        fiatCurrency.shouldBe("EUR")
        partnerInfo.partnerId.value.shouldBe("SignetFaucet")
        partnerInfo.name.shouldBe("Signet Faucet")
      }
  }

  // TODO: add tests for different locales - different currency should be used based on a country locale
  test("get suggested purchase amounts - USD") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    app.partnershipPurchaseService.getSuggestedPurchaseAmounts().shouldBeOk()
      .shouldBe(
        SuggestedPurchaseAmounts(
          default = usd(100.00),
          displayOptions = listOf(
            usd(75.00),
            usd(100.00),
            usd(200.00),
            usd(300.00),
            usd(500.00)
          ),
          min = usd(25.00),
          max = usd(2000.00)
        )
      )
  }

  test("handle redirect and create local partnership transaction") {
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware()

    // Get quote
    val purchaseAmount = eur(10.00)
    val quote = app.partnershipPurchaseService.loadPurchaseQuotes(purchaseAmount).shouldBeOk()
      .single()

    val redirectInfo =
      app.partnershipPurchaseService.preparePurchase(quote, purchaseAmount).shouldBeOk()

    val method = redirectInfo.redirectMethod.shouldBeTypeOf<Web>()
    method.partnerInfo.shouldBe(quote.partnerInfo)
    method.urlString.shouldBe("https://signetfaucet.com/")

    redirectInfo.transaction.run {
      type.shouldBe(PURCHASE)
      partnerInfo.shouldBe(quote.partnerInfo)
      status.shouldBeNull()
      context.shouldBeNull()
      cryptoAmount.shouldBeNull()
      txid.shouldBeNull()
      fiatAmount.shouldBeNull()
      fiatCurrency.shouldBeNull()
      paymentMethod.shouldBeNull()
      sellWalletAddress.shouldBeNull()
      partnerTransactionUrl.shouldBeNull()
    }

    app.partnershipTransactionsService.transactions.test {
      awaitUntil { it.isNotEmpty() }
        .single()
        .shouldBe(redirectInfo.transaction)
    }
  }
})
