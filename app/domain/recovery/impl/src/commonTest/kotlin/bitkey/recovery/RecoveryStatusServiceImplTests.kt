package bitkey.recovery

import bitkey.account.AccountConfigServiceFake
import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.auth.AppGlobalAuthKeyHwSignatureMock
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.factor.PhysicalFactor
import build.wallet.bitkey.keybox.AppKeyBundleMock
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.HwKeyBundleMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.flow.ManualTickerFlow
import build.wallet.coroutines.flow.TickerFlowFactoryImpl
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusF8eClientMock
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.platform.app.AppSessionManagerFake
import build.wallet.recovery.*
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
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
    recoverySyncFrequency = RecoverySyncFrequency(syncFrequency),
    tickerFlowFactory = TickerFlowFactoryImpl()
  )

  beforeTest {
    accountService.reset()
    recoveryDao.reset()
    getRecoveryStatusF8eClient.reset()
    sessionProvider.reset()
    accountConfigService.reset()
    recoveryDao.setLocalRecoveryPresent = Ok(true)
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
      LocalRecoveryAttemptProgress.CompletedRecovery(
        KeyboxMock
      )
    )
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem()
      .shouldBe(
        LocalRecoveryAttemptProgress.CompletedRecovery(
          KeyboxMock
        )
      )
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

  // ===================================================================================
  // Race condition prevention tests
  // ===================================================================================
  //
  // These tests verify two scenarios:
  // 1. Active account device detecting SomeoneElseIsRecovering (SHOULD set server recovery)
  // 2. Lost App recovery device not incorrectly showing SomeoneElseIsRecovering (race condition)

  test("active account: server recovery IS set even when local recovery is NOT present - enables SomeoneElseIsRecovering detection") {
    // When we have an active full account, server recovery should ALWAYS be set
    // to enable detection of someone else trying to recover the account.
    // This tests the legitimate SomeoneElseIsRecovering case.
    accountService.setActiveAccount(FullAccountMock)
    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock
    recoveryDao.setLocalRecoveryPresent = Ok(false) // No local recovery (we're not recovering)

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    // setActiveServerRecovery SHOULD be called because we have an active account
    // This enables SomeoneElseIsRecovering detection
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("Lost App recovery: server recovery NOT set when local recovery is NOT present - prevents race condition") {
    // This is the core race condition test:
    // When doing Lost App recovery (no active account), if server has a recovery but
    // local recovery hasn't been initiated yet, we should NOT set the server recovery
    // in the DAO. Otherwise, the activeRecovery flow would incorrectly return
    // SomeoneElseIsRecovering.
    //
    // This scenario happens when:
    // 1. User initiates recovery (server is notified)
    // 2. Sync worker runs before local recovery is saved
    // 3. Without the fix, user would see SomeoneElseIsRecovering incorrectly
    accountService.reset() // No active account - Lost App recovery scenario
    recoveryDao.recovery = StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.App)
    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock
    recoveryDao.setLocalRecoveryPresent = Ok(false) // Local recovery NOT yet saved

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    // setActiveServerRecovery should NOT be called because:
    // - We don't have an active account (Lost App recovery)
    // - Local recovery is not yet present
    // This prevents the race condition
    recoveryDao.setActiveServerRecoveryCalls.awaitNoEvents(syncFrequency * 2)
  }

  test("Lost App recovery: server recovery IS set when local recovery IS present") {
    // When doing Lost App recovery and local recovery is present, server recovery should be set
    accountService.reset() // No active account - Lost App recovery scenario
    recoveryDao.recovery = StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.App)
    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock
    recoveryDao.setLocalRecoveryPresent = Ok(true) // Local recovery IS present

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    // setActiveServerRecovery should be called because local recovery is present
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("active account: null server recovery is set regardless of local recovery presence") {
    // When server returns null (no recovery), we should always set it
    // to clear any stale server recovery data
    accountService.setActiveAccount(FullAccountMock)
    getRecoveryStatusF8eClient.activeRecovery = null
    recoveryDao.setLocalRecoveryPresent = Ok(false) // No local recovery

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    // setActiveServerRecovery(null) should be called
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("Lost App recovery: null server recovery is set regardless of local recovery presence") {
    // When server returns null during Lost App recovery, we should set it
    accountService.reset() // No active account - Lost App recovery scenario
    recoveryDao.recovery = StillRecoveringInitiatedRecoveryMock.copy(factorToRecover = PhysicalFactor.App)
    getRecoveryStatusF8eClient.activeRecovery = null
    recoveryDao.setLocalRecoveryPresent = Ok(false) // No local recovery

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    // setActiveServerRecovery(null) should be called
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("setLocalRecoveryProgress with CreatedPendingKeybundles triggers immediate sync") {
    // When local recovery is initiated, it should trigger an immediate sync
    // so that the server recovery can be properly associated
    accountService.setActiveAccount(FullAccountMock)
    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock
    recoveryDao.setLocalRecoveryPresent = Ok(true)

    createBackgroundScope().launch {
      recoveryStatusService.executeWork()
    }

    // First sync from the ticker
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)

    // Now initiate local recovery with CreatedPendingKeybundles
    val progress = LocalRecoveryAttemptProgress.CreatedPendingKeybundles(
      fullAccountId = FullAccountIdMock,
      appKeyBundle = AppKeyBundleMock,
      hwKeyBundle = HwKeyBundleMock,
      appGlobalAuthKeyHwSignature = AppGlobalAuthKeyHwSignatureMock,
      lostFactor = PhysicalFactor.App,
      originalAppGlobalAuthKey = null
    )
    recoveryStatusService.setLocalRecoveryProgress(progress)

    // Verify setLocalRecoveryProgress was called
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(progress)

    // An immediate sync should be triggered after CreatedPendingKeybundles,
    // resulting in another setActiveServerRecovery call
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("setLocalRecoveryProgress with non-CreatedPendingKeybundles does NOT trigger immediate sync") {
    // Only CreatedPendingKeybundles should trigger an immediate sync
    accountService.setActiveAccount(FullAccountMock)
    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock
    recoveryDao.setLocalRecoveryPresent = Ok(true)

    val manualSyncTicker = ManualTickerFlow()
    val recoveryStatusServiceWithManualTicker =
      RecoveryStatusServiceImpl(
        accountService = accountService,
        recoveryDao = recoveryDao,
        getRecoveryStatusF8eClient = getRecoveryStatusF8eClient,
        appSessionManager = sessionProvider,
        recoveryLock = RecoveryLockImpl(),
        accountConfigService = accountConfigService,
        recoverySyncFrequency = RecoverySyncFrequency(syncFrequency),
        tickerFlowFactory = manualSyncTicker
      )

    createBackgroundScope().launch {
      recoveryStatusServiceWithManualTicker.executeWork()
    }

    // Trigger initial sync explicitly to avoid time-based ticks.
    manualSyncTicker.tick()
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)

    // Use a different progress type (not CreatedPendingKeybundles)
    val progress = LocalRecoveryAttemptProgress.RotatedAuthKeys
    recoveryStatusServiceWithManualTicker.setLocalRecoveryProgress(progress)
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(progress)

    // No immediate sync should be triggered for RotatedAuthKeys.
    // Since we control ticks, any extra call would only come from the manual trigger.
    yield()
    recoveryDao.setActiveServerRecoveryCalls.expectNoEvents()
  }
})
