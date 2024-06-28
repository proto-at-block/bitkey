package build.wallet.recovery.sweep

import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull

class SweepServiceImplTests : FunSpec({
  val sweepGenerator = SweepGeneratorMock()
  val service = SweepServiceImpl(sweepGenerator)

  val sweep1 = SweepPsbt(
    psbt = PsbtMock.copy(id = "app-1"),
    signingFactor = App,
    sourceKeyset = SpendingKeysetMock
  )
  val sweep2 = SweepPsbt(
    psbt = PsbtMock.copy(id = "app-2"),
    signingFactor = App,
    sourceKeyset = SpendingKeysetMock2
  )

  beforeTest {
    sweepGenerator.reset()
  }

  test("prepareSweep returns null when there are no funds to sweep") {
    sweepGenerator.generateSweepResult = Ok(emptyList())

    service.prepareSweep(KeyboxMock).shouldBeOk(null)
  }

  test("prepareSweep returns a sweep when there are funds to sweep") {
    sweepGenerator.generateSweepResult = Ok(listOf(sweep1, sweep2))

    val sweep = service.prepareSweep(KeyboxMock).shouldBeOk().shouldNotBeNull()
    sweep.unsignedPsbts.shouldContainExactly(sweep1, sweep2)
  }

  test("prepareSweep returns error if sweep generation fails") {
    val error = SweepGeneratorError.FailedToListKeysets
    sweepGenerator.generateSweepResult = Err(error)

    service.prepareSweep(KeyboxMock).shouldBeErr(error)
  }
})
