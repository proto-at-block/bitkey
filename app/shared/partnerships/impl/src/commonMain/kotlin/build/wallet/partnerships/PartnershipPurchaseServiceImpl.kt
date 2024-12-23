package build.wallet.partnerships

import build.wallet.account.AccountService
import build.wallet.bitcoin.address.BitcoinAddressService
import build.wallet.bitkey.account.FullAccount
import build.wallet.di.AppScope
import build.wallet.di.BitkeyInject
import build.wallet.ensure
import build.wallet.ensureNotNull
import build.wallet.f8e.partnerships.*
import build.wallet.logging.logWarn
import build.wallet.money.FiatMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.money.display.FiatCurrencyPreferenceRepository
import build.wallet.partnerships.PartnershipPurchaseService.NoDisplayAmountsError
import build.wallet.partnerships.PartnershipPurchaseService.NoPurchaseOptionsError
import build.wallet.partnerships.PartnershipTransactionType.PURCHASE
import build.wallet.platform.links.AppRestrictions
import com.github.michaelbull.result.Result
import com.github.michaelbull.result.binding
import com.github.michaelbull.result.coroutines.coroutineBinding
import com.github.michaelbull.result.map
import com.ionspin.kotlin.bignum.decimal.toBigDecimal
import kotlinx.coroutines.flow.first

@BitkeyInject(AppScope::class)
class PartnershipPurchaseServiceImpl(
  private val accountService: AccountService,
  private val bitcoinAddressService: BitcoinAddressService,
  private val fiatCurrencyPreferenceRepository: FiatCurrencyPreferenceRepository,
  private val partnershipTransactionsService: PartnershipTransactionsService,
  private val getPurchaseOptionsF8eClient: GetPurchaseOptionsF8eClient,
  private val getPurchaseQuoteListF8eClient: GetPurchaseQuoteListF8eClient,
  private val getPurchaseRedirectF8eClient: GetPurchaseRedirectF8eClient,
) : PartnershipPurchaseService {
  override suspend fun getSuggestedPurchaseAmounts(): Result<SuggestedPurchaseAmounts, Error> =
    coroutineBinding {
      val fiatCurrency = fiatCurrencyPreferenceRepository.fiatCurrencyPreference.value
      val account = accountService.activeAccount().first()
      ensure(account is FullAccount) { Error("Expected active full account.") }
      val options = getPurchaseOptionsF8eClient
        .purchaseOptions(
          fullAccountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          currency = fiatCurrency
        )
        .bind()

      options.toSuggestedPurchaseAmounts(fiatCurrency, SUPPORTED_PAYMENT_METHOD).bind()
    }

  override suspend fun loadPurchaseQuotes(amount: FiatMoney): Result<List<PurchaseQuote>, Error> =
    coroutineBinding {
      val account = accountService.activeAccount().first()
      ensure(account is FullAccount) { Error("Expected active full account.") }
      getPurchaseQuoteListF8eClient
        .purchaseQuotes(
          fullAccountId = account.accountId,
          f8eEnvironment = account.config.f8eEnvironment,
          fiatAmount = amount,
          paymentMethod = SUPPORTED_PAYMENT_METHOD
        )
        .map { it.quoteList }
        .bind()
    }

  override suspend fun preparePurchase(
    quote: PurchaseQuote,
    purchaseAmount: FiatMoney,
  ): Result<PurchaseRedirectInfo, Throwable> =
    coroutineBinding {
      val address = bitcoinAddressService.generateAddress().bind()
      val account = accountService.activeAccount().first()
      ensure(account is FullAccount) { Error("Expected active full account.") }

      val redirectInfo = getPurchaseRedirectF8eClient
        .purchaseRedirect(
          fullAccountId = account.accountId,
          address = address,
          f8eEnvironment = account.config.f8eEnvironment,
          fiatAmount = purchaseAmount,
          partner = quote.partnerInfo.partnerId.value,
          paymentMethod = SUPPORTED_PAYMENT_METHOD,
          quoteId = quote.quoteId
        )
        .bind()
        .redirectInfo

      val partnerTransaction = partnershipTransactionsService
        .create(
          partnerInfo = quote.partnerInfo,
          type = PURCHASE,
          id = redirectInfo.partnerTransactionId
        )
        .bind()

      PurchaseRedirectInfo(
        redirectMethod = redirectInfo.toPartnerRedirectMethod(quote.partnerInfo),
        transaction = partnerTransaction
      )
    }

  private companion object {
    // TODO: W-5675 - defaulting to card for now, but will eventually support other payment methods
    private const val SUPPORTED_PAYMENT_METHOD = "CARD"
    private const val MAX_DISPLAY_OPTIONS = 5
  }

  private fun PurchaseOptions.toSuggestedPurchaseAmounts(
    currency: FiatCurrency,
    paymentMethod: String,
  ): Result<SuggestedPurchaseAmounts, Error> =
    binding {
      val cardPaymentOptions = paymentMethods[paymentMethod]
      ensureNotNull(cardPaymentOptions) {
        NoPurchaseOptionsError(paymentMethod)
      }
      ensure(cardPaymentOptions.displayPurchaseAmounts.isNotEmpty()) {
        NoDisplayAmountsError(paymentMethod)
      }

      val displayOptions = cardPaymentOptions.displayPurchaseAmounts
        .map { FiatMoney(currency, it.toBigDecimal()) }
        .take(MAX_DISPLAY_OPTIONS)
      val defaultAmount =
        FiatMoney(currency, cardPaymentOptions.defaultPurchaseAmount.toBigDecimal())

      if (defaultAmount !in displayOptions) {
        logWarn { "Suggested purchase amounts: default amount is not present in all display options" }
      }

      SuggestedPurchaseAmounts(
        default = defaultAmount,
        displayOptions = displayOptions,
        min = FiatMoney(currency, cardPaymentOptions.minPurchaseAmount.toBigDecimal()),
        max = FiatMoney(currency, cardPaymentOptions.maxPurchaseAmount.toBigDecimal())
      )
    }

  private fun RedirectInfo.toPartnerRedirectMethod(
    partnerInfo: PartnerInfo,
  ): PartnerRedirectionMethod {
    return when (redirectType) {
      RedirectUrlType.DEEPLINK ->
        PartnerRedirectionMethod.Deeplink(
          urlString = url,
          appRestrictions = appRestrictions?.let {
            AppRestrictions(
              packageName = it.packageName,
              minVersion = it.minVersion
            )
          },
          partnerName = redirectType.name
        )
      RedirectUrlType.WIDGET ->
        PartnerRedirectionMethod.Web(
          urlString = url,
          partnerInfo = partnerInfo
        )
    }
  }
}
