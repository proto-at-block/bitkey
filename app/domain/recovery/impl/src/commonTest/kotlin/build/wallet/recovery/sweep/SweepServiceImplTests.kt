@file:OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)

package build.wallet.recovery.sweep

import app.cash.turbine.test
import build.wallet.account.AccountServiceFake
import build.wallet.activity.TransactionActivityOperations
import build.wallet.bitcoin.transactions.PsbtMock
import build.wallet.bitkey.factor.PhysicalFactor.App
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.spending.SpendingKeysetMock
import build.wallet.bitkey.spending.SpendingKeysetMock2
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError
import build.wallet.recovery.sweep.SweepGenerator.SweepGeneratorError.ErrorSyncingSpendingWallet
import build.wallet.testing.shouldBeErr
import build.wallet.testing.shouldBeOk
import build.wallet.worker.BackgroundStrategy
import build.wallet.worker.RefreshOperationFilter
import build.wallet.worker.RunStrategy
import com.github.michaelbull.result.Err
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

class SweepServiceImplTests : FunSpec({
  val sweepGenerator = SweepGeneratorMock()
  val accountService = AccountServiceFake()
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
  val syncFrequency = 20.milliseconds

  fun service() =
    SweepServiceImpl(
      accountService = accountService,
      sweepGenerator = sweepGenerator,
      sweepSyncFrequency = SweepSyncFrequency(syncFrequency)
    )

  beforeTest {
    accountService.reset()
    appSessionManager.reset()
    sweepGenerator.reset()
  }

  test("prepareSweep returns null when there are no funds to sweep") {
    accountService.setActiveAccount(FullAccountMock)
    sweepGenerator.generateSweepResult = Ok(emptyList())

    service().prepareSweep(KeyboxMock).shouldBeOk(null)
  }

  test("prepareSweep returns a sweep when there are funds to sweep") {
    accountService.setActiveAccount(FullAccountMock)
    sweepGenerator.generateSweepResult = Ok(listOf(sweep1, sweep2))

    val sweep = service().prepareSweep(KeyboxMock).shouldBeOk().shouldNotBeNull()
    sweep.unsignedPsbts.shouldContainExactly(sweep1, sweep2)
  }

  test("prepareSweep returns error if sweep generation fails") {
    accountService.setActiveAccount(FullAccountMock)
    val error = SweepGeneratorError.FailedToListKeysets
    sweepGenerator.generateSweepResult = Err(error)

    service().prepareSweep(KeyboxMock).shouldBeErr(error)
  }

  test("sweep is false by default when sync is not running") {
    accountService.setActiveAccount(FullAccountMock)
    val service = service()

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()

      sweepGenerator.generateSweepResult = Ok(availableSweep)
      delay(syncFrequency)
      awaitNoEvents()
    }
  }

  test("sweep is set to true on manual sync") {
    accountService.setActiveAccount(FullAccountMock)
    val service = service()

    val backgroundScope = createBackgroundScope()
    backgroundScope.launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()

      // disable sync
      backgroundScope.cancel()

      delay(syncFrequency)
      awaitNoEvents()

      sweepGenerator.generateSweepResult = Ok(availableSweep)

      service.checkForSweeps()
      awaitItem().shouldBeTrue()
    }
  }

  test("no sweeps available") {
    accountService.setActiveAccount(FullAccountMock)
    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()

      sweepGenerator.generateSweepResult = Ok(emptyList())

      delay(syncFrequency)
      awaitNoEvents()

      service.checkForSweeps()
      awaitNoEvents()
    }
  }

  test("no update if unable to sync") {
    accountService.setActiveAccount(FullAccountMock)
    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()

      sweepGenerator.generateSweepResult = Err(ErrorSyncingSpendingWallet(RuntimeException()))
      delay(syncFrequency)
      awaitNoEvents()

      service.checkForSweeps()
      awaitNoEvents()
    }
  }

  test("no update if there is no active account") {
    accountService.clear()
    sweepGenerator.generateSweepResult = Ok(availableSweep)
    val service = service()

    createBackgroundScope().launch {
      service.executeWork()
    }

    service.sweepRequired.test {
      awaitItem().shouldBeFalse()

      delay(syncFrequency)
      awaitNoEvents()

      service.checkForSweeps()
      awaitNoEvents()
    }
  }

  test("run strategy is defined as expected") {
    val service = service()

    service.runStrategy.shouldContainExactly(
      RunStrategy.Startup(),
      RunStrategy.Refresh(
        type = RefreshOperationFilter.Subset(TransactionActivityOperations)
      ),
      RunStrategy.Periodic(
        interval = syncFrequency,
        backgroundStrategy = BackgroundStrategy.Skip
      )
    )
  }
})
