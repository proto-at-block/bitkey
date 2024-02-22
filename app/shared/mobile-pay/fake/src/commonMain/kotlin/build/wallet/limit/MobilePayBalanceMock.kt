package build.wallet.limit

import build.wallet.money.BitcoinMoney
import build.wallet.money.FiatMoney
import com.ionspin.kotlin.bignum.integer.toBigInteger
import kotlinx.datetime.TimeZone

// Let o = 10_000_000 (number of sats in 1 BTC)
// Let r = 22678 (`LocalRateProvider`'s 1 BTC in USD)
// Let a be amount in USD
// Let s be the amount in sats
// Formula for $s = \frac{o}{r} * \frac{1}{100} * a$
// Formula for $a = s * 100 * \frac{r}{o}$
val ONE_BTC_IN_SATOSHIS = 100_000_000
val LOCAL_ONE_BTC_IN_USD = 22678

val SPENT_SATS = 860_000
val SPENDING_LIMIT_SATS = 1_000_000
val SPENDING_LIMIT_USD = SPENDING_LIMIT_SATS * 100 * (LOCAL_ONE_BTC_IN_USD.toDouble() / ONE_BTC_IN_SATOSHIS)

val MobilePayBalanceMock =
  MobilePayBalance(
    spent = BitcoinMoney.sats(SPENT_SATS),
    available = BitcoinMoney.sats(SPENDING_LIMIT_SATS - SPENT_SATS),
    limit =
      SpendingLimit(
        active = true,
        amount = FiatMoney.usd(cents = SPENDING_LIMIT_USD.toLong().toBigInteger()),
        timezone = TimeZone.UTC
      )
  )
