package build.wallet.bitcoin.fees

import build.wallet.bitcoin.fees.FeeRateComparison.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class FeeRateComparisonTests : FunSpec({

  test("returns EQUAL when rates are within 1 percent") {
    // Exactly equal
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(EQUAL)

    // 0.5% higher (within 1%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.05f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(EQUAL)

    // 0.5% lower (within 1%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.95f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(EQUAL)
  }

  test("returns SLIGHTLY_HIGHER when augur is 1 to 5 percent higher") {
    // 1% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.1f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SLIGHTLY_HIGHER)

    // 3% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.3f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SLIGHTLY_HIGHER)

    // 4.9% higher (just under 5%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.49f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SLIGHTLY_HIGHER)
  }

  test("returns SLIGHTLY_LOWER when augur is 1 to 5 percent lower") {
    // 1% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.9f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SLIGHTLY_LOWER)

    // 3% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.7f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SLIGHTLY_LOWER)

    // 4.9% lower (just under 5%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.51f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SLIGHTLY_LOWER)
  }

  test("returns HIGHER when augur is 5 to 10 percent higher") {
    // 5% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.5f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(HIGHER)

    // 7% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.7f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(HIGHER)

    // 9.9% higher (just under 10%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.99f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(HIGHER)
  }

  test("returns LOWER when augur is 5 to 10 percent lower") {
    // 5% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.5f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(LOWER)

    // 7% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.3f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(LOWER)

    // 9.9% lower (just under 10%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.01f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(LOWER)
  }

  test("returns MUCH_HIGHER when augur is 10 to 20 percent higher") {
    // 10% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(11.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(MUCH_HIGHER)

    // 15% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(11.5f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(MUCH_HIGHER)

    // 19.9% higher (just under 20%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(11.99f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(MUCH_HIGHER)
  }

  test("returns MUCH_LOWER when augur is 10 to 20 percent lower") {
    // 10% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(9.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(MUCH_LOWER)

    // 15% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(8.5f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(MUCH_LOWER)

    // 19.9% lower (just under 20%)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(8.01f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(MUCH_LOWER)
  }

  test("returns SIGNIFICANTLY_HIGHER when augur is more than 20 percent higher") {
    // 20% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(12.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SIGNIFICANTLY_HIGHER)

    // 50% higher
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(15.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SIGNIFICANTLY_HIGHER)

    // 100% higher (double)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(20.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SIGNIFICANTLY_HIGHER)
  }

  test("returns SIGNIFICANTLY_LOWER when augur is more than 20 percent lower") {
    // 20% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(8.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SIGNIFICANTLY_LOWER)

    // 50% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(5.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SIGNIFICANTLY_LOWER)

    // 80% lower
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(2.0f),
      mempoolFeeRate = FeeRate(10.0f)
    ).shouldBe(SIGNIFICANTLY_LOWER)
  }

  test("handles edge case when mempool rate is zero") {
    // Augur positive, mempool zero
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.0f),
      mempoolFeeRate = FeeRate(0.0f)
    ).shouldBe(SIGNIFICANTLY_HIGHER)

    // Both zero
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(0.0f),
      mempoolFeeRate = FeeRate(0.0f)
    ).shouldBe(EQUAL)
  }

  test("handles edge case when mempool rate is negative") {
    // Augur positive, mempool negative (shouldn't happen but handling gracefully)
    FeeRateComparison.compare(
      augurFeeRate = FeeRate(10.0f),
      mempoolFeeRate = FeeRate(-1.0f)
    ).shouldBe(SIGNIFICANTLY_HIGHER)
  }
})
