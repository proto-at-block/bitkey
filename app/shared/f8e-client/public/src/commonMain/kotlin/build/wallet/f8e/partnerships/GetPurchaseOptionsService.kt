package build.wallet.f8e.partnerships

import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.f8e.F8eEnvironment
import build.wallet.ktor.result.NetworkingError
import build.wallet.money.currency.FiatCurrency
import com.github.michaelbull.result.Result

/**
 * Service used to render purchase amounts in a fiat currency to purchase Bitcoin from partners
 */
interface GetPurchaseOptionsService {
  suspend fun purchaseOptions(
    fullAccountId: FullAccountId,
    f8eEnvironment: F8eEnvironment,
    currency: FiatCurrency,
  ): Result<PurchaseOptions, NetworkingError>
}
