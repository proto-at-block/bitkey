package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.FiatMoney
import com.github.michaelbull.result.Result

interface GetPurchaseQuoteListF8eClient {
  suspend fun purchaseQuotes(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    fiatAmount: FiatMoney,
    paymentMethod: String,
  ): Result<Success, NetworkingError>

  /**
   * A struct containing a list of partner purchase quotes
   */
  data class Success(val quoteList: List<Quote>)
}
