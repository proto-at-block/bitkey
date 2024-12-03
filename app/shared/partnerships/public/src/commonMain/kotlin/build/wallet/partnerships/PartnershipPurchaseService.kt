package build.wallet.partnerships

import build.wallet.money.FiatMoney
import com.github.michaelbull.result.Result

/**
 * Domain service for purchasing bitcoin through partners.
 */
interface PartnershipPurchaseService {
  /**
   * Returns suggested fiat amounts when customer purchases bitcoin through a partner.
   * See [SuggestedPurchaseAmounts].
   */
  suspend fun getSuggestedPurchaseAmounts(): Result<SuggestedPurchaseAmounts, Error>

  /**
   * Returns quotes from available partners for purchasing bitcoin amount worth fiat [amount].
   */
  suspend fun loadPurchaseQuotes(amount: FiatMoney): Result<List<PurchaseQuote>, Error>

  suspend fun preparePurchase(
    quote: PurchaseQuote,
    purchaseAmount: FiatMoney,
  ): Result<PurchaseRedirectInfo, Throwable>

  class NoDisplayAmountsError(paymentMethod: String) :
    Error("No display amounts available for payment method: $paymentMethod")

  class NoPurchaseOptionsError(paymentMethod: String) :
    Error("No purchase options available for payment method: $paymentMethod")
}
