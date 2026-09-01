package build.wallet.partnerships

import build.wallet.money.BitcoinMoney
import build.wallet.money.exchange.ExchangeRate
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import kotlinx.collections.immutable.ImmutableList

class PartnershipSaleServiceFake : PartnershipSaleService {
  var sellLimits: Result<SellLimits, Error> = Ok(SellLimitsFake)

  override suspend fun getSellLimits(
    exchangeRates: ImmutableList<ExchangeRate>,
  ): Result<SellLimits, Error> {
    return sellLimits
  }

  fun reset() {
    sellLimits = Ok(SellLimitsFake)
  }
}

val SellLimitsFake = SellLimits(
  minAmount = BitcoinMoney.btc(0.0005),
  maxAmount = BitcoinMoney.btc(1.0)
)
