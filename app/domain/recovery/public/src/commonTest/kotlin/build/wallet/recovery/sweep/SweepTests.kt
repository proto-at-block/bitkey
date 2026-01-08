package build.wallet.recovery.sweep

import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.spending.PrivateSpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock
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
        signaturePlan = SweepSignaturePlan.AppAndServer,
        sourceKeyset = SpendingKeysetMock,
        destinationAddress = "bc1qtest"
      )
      val sweepPsbt2 = SweepPsbt(
        psbt = PsbtMock,
        signaturePlan = SweepSignaturePlan.HardwareAndServer,
        sourceKeyset = PrivateSpendingKeysetMock,
        destinationAddress = "bc1qtest"
      )
      val sweep = Sweep(unsignedPsbts = setOf(sweepPsbt1, sweepPsbt2))

      sweep.psbtsRequiringHwSign.shouldContainExactly(sweepPsbt2)
    }

    test("total fee amount is calculated using psbts") {
      val sweepPsbt1 = SweepPsbt(
        psbt = PsbtMock,
        signaturePlan = SweepSignaturePlan.AppAndServer,
        sourceKeyset = SpendingKeysetMock,
        destinationAddress = "bc1qtest"
      )
      val sweepPsbt2 = SweepPsbt(
        psbt = PsbtMock,
        signaturePlan = SweepSignaturePlan.HardwareAndServer,
        sourceKeyset = PrivateSpendingKeysetMock,
        destinationAddress = "bc1qtest"
      )
      val sweep = Sweep(unsignedPsbts = setOf(sweepPsbt1, sweepPsbt2))

      val expectedFee = sweepPsbt1.psbt.fee.amount + sweepPsbt2.psbt.fee.amount
      sweep.totalFeeAmount.shouldBe(expectedFee)
    }

    test("destination address is returned from first psbt") {
      val sweepPsbt1 = SweepPsbt(
        psbt = PsbtMock,
        signaturePlan = SweepSignaturePlan.AppAndServer,
        sourceKeyset = SpendingKeysetMock,
        destinationAddress = "bc1qfirstaddress"
      )
      val sweepPsbt2 = SweepPsbt(
        psbt = PsbtMock,
        signaturePlan = SweepSignaturePlan.HardwareAndServer,
        sourceKeyset = PrivateSpendingKeysetMock,
        destinationAddress = "bc1qfirstaddress"
      )
      val sweep = Sweep(unsignedPsbts = setOf(sweepPsbt1, sweepPsbt2))

      sweep.destinationAddress.shouldBe("bc1qfirstaddress")
    }
  }
})
