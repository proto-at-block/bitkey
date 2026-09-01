package build.wallet.partnerships

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.partnerships.GetSaleOptionsF8eClientMock
import build.wallet.f8e.partnerships.SaleOptionsFake
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue.DoubleFlag
import build.wallet.feature.flags.SellBitcoinMaxAmountFeatureFlag
import build.wallet.feature.flags.SellBitcoinMinAmountFeatureFlag
import build.wallet.ktor.result.HttpError.NetworkError
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.code.IsoCurrencyTextCode
import build.wallet.money.display.FiatCurrencyPreferenceRepositoryMock
import build.wallet.money.exchange.CurrencyConverterFake
import build.wallet.money.exchange.ExchangeRate
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import kotlinx.collections.immutable.persistentListOf
import kotlinx.datetime.Clock

class PartnershipSaleServiceImplTests : FunSpec({
  val accountService = AccountServiceFake()
  val fiatCurrencyPreferenceRepository = FiatCurrencyPreferenceRepositoryMock(turbines::create)
  val getSaleOptionsF8eClient = GetSaleOptionsF8eClientMock(turbines::create)
  val currencyConverter = CurrencyConverterFake(conversionRate = 0.00002)
  val featureFlagDao = FeatureFlagDaoFake()
  val sellBitcoinMinAmountFeatureFlag = SellBitcoinMinAmountFeatureFlag(featureFlagDao)
  val sellBitcoinMaxAmountFeatureFlag = SellBitcoinMaxAmountFeatureFlag(featureFlagDao)
  val service = PartnershipSaleServiceImpl(
    accountService = accountService,
    fiatCurrencyPreferenceRepository = fiatCurrencyPreferenceRepository,
    getSaleOptionsF8eClient = getSaleOptionsF8eClient,
    currencyConverter = currencyConverter,
    sellBitcoinMinAmountFeatureFlag = sellBitcoinMinAmountFeatureFlag,
    sellBitcoinMaxAmountFeatureFlag = sellBitcoinMaxAmountFeatureFlag
  )

  beforeTest {
    accountService.reset()
    accountService.setActiveAccount(FullAccountMock)
    fiatCurrencyPreferenceRepository.reset()
    featureFlagDao.reset()
    getSaleOptionsF8eClient.saleOptionsResult = Ok(SaleOptionsFake)
    currencyConverter.reset()
    currencyConverter.conversionRate = 0.00002
  }

  test("gets server sale options and converts fiat limits to BTC") {
    service.getSellLimits(exchangeRates).shouldBeOk(
      SellLimits(
        minAmount = BitcoinMoney.btc(0.0002),
        maxAmount = BitcoinMoney.btc(1.0)
      )
    )
    getSaleOptionsF8eClient.getSaleOptionsCall.awaitItem()
  }

  test("falls back to feature flag limits when server sale options are empty") {
    getSaleOptionsF8eClient.saleOptionsResult = Ok(
      SaleOptions(
        country = "US",
        fiatCurrency = "USD",
        minFiatAmount = 0.0,
        maxFiatAmount = 0.0,
        partnerLimits = emptyList()
      )
    )

    service.getSellLimits(exchangeRates).shouldBeOk(
      SellLimits(
        minAmount = BitcoinMoney.btc(0.0005),
        maxAmount = BitcoinMoney.btc(0.5)
      )
    )
    getSaleOptionsF8eClient.getSaleOptionsCall.awaitItem()
  }

  test("falls back to feature flag limits when server sale options fail") {
    sellBitcoinMinAmountFeatureFlag.setFlagValue(DoubleFlag(0.001))
    sellBitcoinMaxAmountFeatureFlag.setFlagValue(DoubleFlag(0.75))
    getSaleOptionsF8eClient.saleOptionsResult = Err(NetworkError(Error("network failed")))

    service.getSellLimits(exchangeRates).shouldBeOk(
      SellLimits(
        minAmount = BitcoinMoney.btc(0.001),
        maxAmount = BitcoinMoney.btc(0.75)
      )
    )
    getSaleOptionsF8eClient.getSaleOptionsCall.awaitItem()
  }
})

private val exchangeRates = persistentListOf(
  ExchangeRate(
    fromCurrency = IsoCurrencyTextCode("BTC"),
    toCurrency = IsoCurrencyTextCode("USD"),
    rate = 50_000.0,
    timeRetrieved = Clock.System.now()
  )
)
