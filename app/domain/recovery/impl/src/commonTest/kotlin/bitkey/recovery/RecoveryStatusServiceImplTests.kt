package bitkey.recovery

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusF8eClientMock
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.recovery.*
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class RecoveryStatusServiceImplTests : FunSpec({

  val accountService = AccountServiceFake()
  val recoveryDao = RecoveryDaoMock(turbines::create)
  val getRecoveryStatusF8eClient = GetDelayNotifyRecoveryStatusF8eClientMock()
  val sessionProvider = AppSessionManagerFake()
  val syncFrequency = 100.milliseconds
  val accountConfigService = AccountConfigServiceFake()
  val recoveryStatusService = RecoveryStatusServiceImpl(
    accountService = accountService,
    recoveryDao = recoveryDao,
    getRecoveryStatusF8eClient = getRecoveryStatusF8eClient,
    appSessionManager = sessionProvider,
    recoveryLock = RecoveryLockImpl(),
    accountConfigService = accountConfigService,
    recoverySyncFrequency = RecoverySyncFrequency(syncFrequency)
  )

  beforeTest {
    accountService.reset()
    recoveryDao.reset()
    getRecoveryStatusF8eClient.reset()
    sessionProvider.reset()
    accountConfigService.reset()
  }

  test("server polling sync recovery is retrieved (non-null) and cached in dao.") {
    accountService.setActiveAccount(FullAccountMock)
    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)

    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock
    // emit again after the polling duration
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("set local recovery progress") {
    recoveryStatusService.setLocalRecoveryProgress(
      LocalRecoveryAttemptProgress.SweptFunds(
        KeyboxMock
      )
    )
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(Unit)
  }

  test("clear") {
    recoveryStatusService.clear()
    recoveryDao.clearCalls.awaitItem().shouldBe(Unit)
  }

  test("recovery sync worker resets when account changes") {
    accountService.setActiveAccount(FullAccountMock)

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)

    accountService.setActiveAccount(LiteAccountMock)
    recoveryDao.setActiveServerRecoveryCalls.awaitNoEvents(syncFrequency)
  }

  test("recovery sync worker does not run in the background when there is active full account") {
    accountService.setActiveAccount(FullAccountMock)
    sessionProvider.appDidEnterBackground()

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }
    recoveryDao.setActiveServerRecoveryCalls.awaitNoEvents(syncFrequency)

    sessionProvider.appDidEnterForeground()
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("recovery sync worker does not run in the background when there is active Lost App recovery") {
    recoveryDao.recovery = StillRecoveringInitiatedRecoveryMock
    recoveryDao.recovery =
      StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.App)
    sessionProvider.appDidEnterBackground()

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    recoveryDao.setActiveServerRecoveryCalls.awaitNoEvents(syncFrequency)

    sessionProvider.appDidEnterForeground()
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("recovery sync worker doesn't execute when there is no active full account") {
    accountService.setActiveAccount(LiteAccountMock)

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    recoveryDao.setActiveServerRecoveryCalls.awaitNoEvents(syncFrequency)
  }

  test("recovery worker executes when there is active account with Lost Hardware recovery") {
    accountService.setActiveAccount(FullAccountMock)
    recoveryDao.recovery =
      StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.Hardware)

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }
})
