package build.wallet.partnerships

import build.wallet.account.AccountService
import build.wallet.account.getAccount
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.f8e.partnerships.GetSaleOptionsF8eClient
import build.wallet.feature.flags.SellBitcoinMaxAmountFeatureFlag
import build.wallet.feature.flags.SellBitcoinMinAmountFeatureFlag
import build.wallet.logging.logWarn
import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.money.exchange.CurrencyConverter
import build.wallet.money.exchange.ExchangeRate
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.getOrElse
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.collections.immutable.ImmutableList

@BitkeyInject(AppScope::class)
class PartnershipSaleServiceImpl(
  private val accountService: AccountService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val getSaleOptionsF8eClient: GetSaleOptionsF8eClient,
  private val currencyConverter: CurrencyConverter,
  private val sellBitcoinMinAmountFeatureFlag: SellBitcoinMinAmountFeatureFlag,
  private val sellBitcoinMaxAmountFeatureFlag: SellBitcoinMaxAmountFeatureFlag,
) : PartnershipSaleService {
  override suspend fun getSellLimits(
    exchangeRates: ImmutableList<ExchangeRate>,
  ): Result<SellLimits, Error> =
    coroutineBinding {
      val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
      val account = accountService.getAccount<FullAccount>().bind()
      val saleOptions = getSaleOptionsF8eClient
        .saleOptions(
          fullAccountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          currency = fiatCurrency
        )
        .getOrElse { error ->
          logWarn(throwable = error) {
            "Unable to load server-driven sell limits; using fallback feature flags"
          }
          return@coroutineBinding fallbackSellLimits().bind()
        }

      saleOptions.toSellLimits(fiatCurrency, exchangeRates)
        .getOrElse { error ->
          logWarn(throwable = error) {
            "Unable to use server-driven sell limits; using fallback feature flags"
          }
          fallbackSellLimits().bind()
        }
    }

  private suspend fun SaleOptions.toSellLimits(
    fiatCurrency: FiatCurrency,
    exchangeRates: ImmutableList<ExchangeRate>,
  ): Result<SellLimits, Error> =
    coroutineBinding {
      ensure(partnerLimits.isNotEmpty()) {
        Error("No sell options available")
      }

      val minAmount = ensureNotNull(
        currencyConverter.convert(
          FiatMoney(fiatCurrency, minFiatAmount.toBigDecimal()),
          BitcoinMoney.zero().currency,
          exchangeRates
        ) as? BitcoinMoney
      ) {
        Error("Unable to convert minimum sell amount to BTC")
      }
      val maxAmount = ensureNotNull(
        currencyConverter.convert(
          FiatMoney(fiatCurrency, maxFiatAmount.toBigDecimal()),
          BitcoinMoney.zero().currency,
          exchangeRates
        ) as? BitcoinMoney
      ) {
        Error("Unable to convert maximum sell amount to BTC")
      }

      SellLimits(
        minAmount = minAmount,
        maxAmount = maxAmount
      )
    }

  private suspend fun fallbackSellLimits(): Result<SellLimits, Error> =
    coroutineBinding {
      SellLimits(
        minAmount = BitcoinMoney.btc(sellBitcoinMinAmountFeatureFlag.flagValue().value.value),
        maxAmount = BitcoinMoney.btc(sellBitcoinMaxAmountFeatureFlag.flagValue().value.value)
      )
    }
}
