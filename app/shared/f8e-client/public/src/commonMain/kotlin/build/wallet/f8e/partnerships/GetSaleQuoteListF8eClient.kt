package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.BitcoinMoney
import build.wallet.money.currency.FiatCurrency
import build.wallet.partnerships.SaleQuote
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

interface GetSaleQuoteListF8eClient {
  suspend fun getSaleQuotes(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    cryptoAmount: BitcoinMoney,
    fiatCurrency: FiatCurrency,
  ): Result<Success, NetworkingError>

  /**
   * A struct containing a list of sale quotes
   */
  data class Success(val quotes: ImmutableList<SaleQuote>)
}
