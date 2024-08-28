@file:OptIn(ExperimentalCoroutinesApi::class)

package build.wallet.recovery.sweep

import app.cash.turbine.test
import build.wallet.account.AccountRepositoryFake
import build.wallet.analytics.events.AppSessionManagerFake
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.flags.PromptSweepFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.coroutines.backgroundScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class SweepServiceImplTests : FunSpec({
  coroutineTestScope = true

  val sweepGenerator = SweepGeneratorMock()
  val featureFlagDao = FeatureFlagDaoMock()
  val flag = PromptSweepFeatureFlag(featureFlagDao = featureFlagDao)
  val accountRepository = AccountRepositoryFake()
  val appSessionManager = AppSessionManagerFake()

  val availableSweep = listOf(
    SweepPsbt(
      psbt = PsbtMock.copy(id = "app-1"),
      signingFactor = App,
      sourceKeyset = SpendingKeysetMock
    )
  )

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

  fun service() =
    SweepServiceImpl(
      accountRepository = accountRepository,
      appSessionManager = appSessionManager,
      promptSweepFeatureFlag = flag,
      sweepGenerator = sweepGenerator
    )

  beforeTest {
    accountRepository.reset()
    appSessionManager.reset()
    sweepGenerator.reset()
  }

  test("prepareSweep returns null when there are no funds to sweep") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult = Ok(emptyList())

    service().prepareSweep(KeyboxMock).shouldBeOk(null)
  }

  test("prepareSweep returns a sweep when there are funds to sweep") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult = Ok(listOf(sweep1, sweep2))

    val sweep = service().prepareSweep(KeyboxMock).shouldBeOk().shouldNotBeNull()
    sweep.unsignedPsbts.shouldContainExactly(sweep1, sweep2)
  }

  test("prepareSweep returns error if sweep generation fails") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    val error = SweepGeneratorError.FailedToListKeysets
    sweepGenerator.generateSweepResult = Err(error)

    service().prepareSweep(KeyboxMock).shouldBeErr(error)
  }

  test("sweep is false by default without sync") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult = Ok(availableSweep)

    val service = service()

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()
    }
  }

  test("sweep is set to true on periodic sync") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult = Ok(availableSweep)
    val service = service()

    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()
      awaitItem().shouldBeTrue()
    }
  }

  test("sweep is set to true on manual sync") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    val service = service()

    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()

      // disable sync
      backgroundScope.cancel()

      expectNoEvents()

      sweepGenerator.generateSweepResult = Ok(availableSweep)
      service.checkForSweeps()
      awaitItem().shouldBeTrue()
    }
  }

  test("no sweeps available") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult = Ok(emptyList())
    val service = service()

    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()
      service.checkForSweeps()
      expectNoEvents()
    }
  }

  test("no update if unable to sync") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult =
      Err(SweepGeneratorError.ErrorSyncingSpendingWallet(RuntimeException()))
    val service = service()

    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()
      service.checkForSweeps()
    }
  }

  test("no update if flag disabled") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(false)
    sweepGenerator.generateSweepResult = Ok(availableSweep)
    val service = service()

    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()
      service.checkForSweeps()
    }
  }

  test("no update if there is no active account") {
    accountRepository.clear()
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult = Ok(availableSweep)
    val service = service()

    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()
      service.checkForSweeps()
    }
  }

  test("sync is disabled when app is in background") {
    accountRepository.setActiveAccount(FullAccountMock)
    flag.setFlagValue(true)
    sweepGenerator.generateSweepResult = Ok(availableSweep)
    val service = service()

    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()
      appSessionManager.appDidEnterBackground()
      expectNoEvents()
    }
  }
})
