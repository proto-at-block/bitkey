package build.wallet.recovery.sweep

import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.factor.PhysicalFactor.Hardware
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

class SweepTests : FunSpec({
  context("Sweep.HasFunds") {
    test("sweep should have psbts") {
      shouldThrow<IllegalArgumentException> {
        Sweep(unsignedPsbts = emptySet())
      }
    }

    test("needsHwSign returns psbts that require hardware factor") {
      val sweepPsbt1 = SweepPsbt(
        psbt = PsbtMock,
        signingFactor = App,
        sourceKeyset = SpendingKeysetMock
      )
      val sweepPsbt2 = SweepPsbt(
        psbt = PsbtMock,
        signingFactor = Hardware,
        sourceKeyset = SpendingKeysetMock2
      )
      val sweep = Sweep(unsignedPsbts = setOf(sweepPsbt1, sweepPsbt2))

      sweep.psbtsRequiringHwSign.shouldContainExactly(sweepPsbt2)
    }

    test("total fee amount is calculated using psbts") {
      val sweepPsbt1 = SweepPsbt(
        psbt = PsbtMock,
        signingFactor = App,
        sourceKeyset = SpendingKeysetMock
      )
      val sweepPsbt2 = SweepPsbt(
        psbt = PsbtMock,
        signingFactor = Hardware,
        sourceKeyset = SpendingKeysetMock2
      )
      val sweep = Sweep(unsignedPsbts = setOf(sweepPsbt1, sweepPsbt2))

      val expectedFee = sweepPsbt1.psbt.fee + sweepPsbt2.psbt.fee
      sweep.totalFeeAmount.shouldBe(expectedFee)
    }
  }
})
