package build.wallet.partnerships

import build.wallet.money.exchange.ExchangeRate
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

/**
 * Domain service for selling bitcoin through partners.
 */
interface PartnershipSaleService {
  /**
   * Returns server-driven sell limits converted to BTC for amount entry.
   */
  suspend fun getSellLimits(
    exchangeRates: ImmutableList<ExchangeRate>,
  ): Result<SellLimits, Error>
}
