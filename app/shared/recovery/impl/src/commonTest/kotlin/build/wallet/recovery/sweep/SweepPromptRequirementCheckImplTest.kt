package build.wallet.recovery.sweep

import app.cash.turbine.test
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.Keybox
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.PromptSweepFeatureFlag
import build.wallet.feature.setFlagValue
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import com.github.michaelbull.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue

class SweepPromptRequirementCheckImplTest : FunSpec({
  val flag = PromptSweepFeatureFlag(
    featureFlagDao = FeatureFlagDaoMock()
  )
  val sweepAvailable = object : SweepGenerator {
    override suspend fun generateSweep(
      keybox: Keybox,
    ): Result<List<SweepPsbt>, SweepGenerator.SweepGeneratorError> {
      return Ok(
        listOf(
          SweepPsbt(
            psbt = PsbtMock.copy(id = "app-1"),
            signingFactor = PhysicalFactor.App,
            sourceKeyset = SpendingKeysetMock
          )
        )
      )
    }
  }
  val noSweeps = object : SweepGenerator {
    override suspend fun generateSweep(
      keybox: Keybox,
    ): Result<List<SweepPsbt>, SweepGenerator.SweepGeneratorError> {
      return Ok(emptyList())
    }
  }
  val sweepGenerateError = object : SweepGenerator {
    override suspend fun generateSweep(
      keybox: Keybox,
    ): Result<List<SweepPsbt>, SweepGenerator.SweepGeneratorError> {
      return Err(SweepGenerator.SweepGeneratorError.ErrorSyncingSpendingWallet(RuntimeException()))
    }
  }

  afterTest {
    flag.reset()
  }

  test("sweep is false by default") {
    flag.setFlagValue(true)
    val sweepPromptRequirementCheck = SweepPromptRequirementCheckImpl(
      promptSweepFeatureFlag = flag,
      sweepGenerator = sweepAvailable
    )
    sweepPromptRequirementCheck.sweepRequired.test {
      awaitItem().shouldBeFalse()
      ensureAllEventsConsumed()
    }
  }

  test("sweep is set to true on sync") {
    flag.setFlagValue(true)
    val sweepPromptRequirementCheck = SweepPromptRequirementCheckImpl(
      promptSweepFeatureFlag = flag,
      sweepGenerator = sweepAvailable
    )
    sweepPromptRequirementCheck.sweepRequired.test {
      awaitItem().shouldBeFalse()
      sweepPromptRequirementCheck.checkForSweeps(KeyboxMock)
      awaitItem().shouldBeTrue()
      ensureAllEventsConsumed()
    }
  }

  test("No sweeps available") {
    flag.setFlagValue(true)
    val sweepPromptRequirementCheck = SweepPromptRequirementCheckImpl(
      promptSweepFeatureFlag = flag,
      sweepGenerator = noSweeps
    )
    sweepPromptRequirementCheck.sweepRequired.test {
      awaitItem().shouldBeFalse()
      sweepPromptRequirementCheck.checkForSweeps(KeyboxMock)
      ensureAllEventsConsumed()
    }
  }

  test("No update if unable to sync") {
    flag.setFlagValue(true)
    val sweepPromptRequirementCheck = SweepPromptRequirementCheckImpl(
      promptSweepFeatureFlag = flag,
      sweepGenerator = sweepGenerateError
    )
    sweepPromptRequirementCheck.sweepRequired.test {
      awaitItem().shouldBeFalse()
      sweepPromptRequirementCheck.checkForSweeps(KeyboxMock)
      ensureAllEventsConsumed()
    }
  }

  test("No update if flag disabled") {
    flag.setFlagValue(false)
    val sweepPromptRequirementCheck = SweepPromptRequirementCheckImpl(
      promptSweepFeatureFlag = flag,
      sweepGenerator = sweepAvailable
    )
    sweepPromptRequirementCheck.sweepRequired.test {
      awaitItem().shouldBeFalse()
      sweepPromptRequirementCheck.checkForSweeps(KeyboxMock)
      ensureAllEventsConsumed()
    }
  }
})
