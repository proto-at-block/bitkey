package build.wallet.money.matchers

import build.wallet.money.Money
import build.wallet.money.currency.BTC
import build.wallet.money.currency.Currency
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.should

fun haveCurrency(currency: Currency) =
  Matcher<Money> { value ->
    MatcherResult(
      value.currency == currency,
      { "expected $value to have currency $currency" },
      { "expected $value to not have currency $currency" }
    )
  }

fun Money.shouldHaveCurrency(currency: Currency): Money {
  this should haveCurrency(currency)
  return this
}

fun beBitcoin() = haveCurrency(BTC)

fun Money.shouldBeBitcoin(): Money {
  this should beBitcoin()
  return this
}

fun beZero() =
  Matcher<Money> { value ->
    MatcherResult(
      value.isZero,
      { "expected $value to be zero" },
      { "expected $value to not be zero" }
    )
  }

fun Money.shouldBeZero(): Money {
  this should beZero()
  return this
}

fun bePositive() =
  Matcher<Money> { value ->
    MatcherResult(
      value.isPositive,
      { "expected $value to be positive" },
      { "expected $value to not be positive" }
    )
  }

fun Money.shouldBePositive(): Money {
  this should bePositive()
  return this
}

fun beNegative() =
  Matcher<Money> { value ->
    MatcherResult(
      value.isNegative,
      { "expected $value to be negative" },
      { "expected $value to not be negative" }
    )
  }

fun Money.shouldBeNegative(): Money {
  this should beNegative()
  return this
}

fun beGreaterThan(other: Money) =
  Matcher<Money> { value ->
    MatcherResult(
      value > other,
      { "expected $value to be greater than $other" },
      { "expected $value to not be greater than $other" }
    )
  }

fun Money.shouldBeGreaterThan(other: Money): Money {
  this should beGreaterThan(other)
  return this
}

fun beGreaterThanOrEqualTo(other: Money) =
  Matcher<Money> { value ->
    MatcherResult(
      value >= other,
      { "expected $value to be greater than or equal to $other" },
      { "expected $value to not be greater than or equal to $other" }
    )
  }

fun Money.shouldBeGreaterThanOrEqualTo(other: Money): Money {
  this should beGreaterThanOrEqualTo(other)
  return this
}

fun beLessThan(other: Money) =
  Matcher<Money> { value ->
    MatcherResult(
      value < other,
      { "expected $value to be less than $other" },
      { "expected $value to not be less than $other" }
    )
  }

fun Money.shouldBeLessThan(other: Money): Money {
  this should beLessThan(other)
  return this
}

fun beLessThanOrEqualTo(other: Money) =
  Matcher<Money> { value ->
    MatcherResult(
      value <= other,
      { "expected $value to be less than or equal to $other" },
      { "expected $value to not be less than or equal to $other" }
    )
  }

fun Money.shouldBeLessThanOrEqualTo(other: Money): Money {
  this should beLessThanOrEqualTo(other)
  return this
}
