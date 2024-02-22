package build.wallet.bitcoin.wallet

import build.wallet.bitcoin.balance.BitcoinBalance
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should
import io.kotest.matchers.shouldNot

fun beZero() =
  Matcher<BitcoinBalance> { value ->
    MatcherResult(
      value == BitcoinBalance.ZeroBalance,
      { "expected $value to be zero balance" },
      { "expected $value to not be zero balance" }
    )
  }

fun BitcoinBalance.shouldBeZero(): BitcoinBalance {
  this should beZero()
  return this
}

fun BitcoinBalance.shouldNotBeZero(): BitcoinBalance {
  this shouldNot beZero()
  return this
}
