package build.wallet.testing.matchers

import com.ionspin.kotlin.bignum.BigNumber
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult

fun <BigType : BigNumber<BigType>> beZero() =
  Matcher<BigNumber<BigType>> { value ->
    MatcherResult(
      value.isZero(),
      { "expected $value to be zero" },
      { "expected $value to not be zero" }
    )
  }

fun <BigType : BigNumber<BigType>> bePositive() =
  Matcher<BigNumber<BigType>> { value ->
    MatcherResult(
      value.isPositive,
      { "expected $value to be positive" },
      { "expected $value to not be positive" }
    )
  }

fun <BigType : BigNumber<BigType>> beNegative() =
  Matcher<BigNumber<BigType>> { value ->
    MatcherResult(
      value.isNegative,
      { "expected $value to be negative" },
      { "expected $value to not be negative" }
    )
  }

fun <BigType : BigNumber<BigType>> beGreaterThan(other: Any) =
  Matcher<BigNumber<BigType>> { value ->
    MatcherResult(
      value.compareTo(other) > 0,
      { "expected $value to be greater than $other" },
      { "expected $value to not be greater than $other" }
    )
  }

fun <BigType : BigNumber<BigType>> beGreaterThanOrEqualTo(other: Any) =
  Matcher<BigNumber<BigType>> { value ->
    MatcherResult(
      value.compareTo(other) >= 0,
      { "expected $value to be greater than or equal to $other" },
      { "expected $value to not be greater than or equal to $other" }
    )
  }

fun <BigType : BigNumber<BigType>> beLessThan(other: Any) =
  Matcher<BigNumber<BigType>> { value ->
    MatcherResult(
      value.compareTo(other) < 0,
      { "expected $value to be less than $other" },
      { "expected $value to not be less than $other" }
    )
  }

fun <BigType : BigNumber<BigType>> beLessThanOrEqualTo(other: Any) =
  Matcher<BigNumber<BigType>> { value ->
    MatcherResult(
      value.compareTo(other) <= 0,
      { "expected $value to be less than or equal to $other" },
      { "expected $value to not be less than or equal to $other" }
    )
  }
