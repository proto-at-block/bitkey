package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import build.wallet.partnerships.SaleOptions
import com.github.michaelbull.result.Result

interface GetSaleOptionsF8eClient {
  suspend fun saleOptions(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    currency: FiatCurrency,
  ): Result<SaleOptions, NetworkingError>
}
