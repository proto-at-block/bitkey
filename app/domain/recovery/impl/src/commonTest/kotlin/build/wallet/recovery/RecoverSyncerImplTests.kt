@file:OptIn(DelicateCoroutinesApi::class)

package build.wallet.recovery

import bitkey.account.AccountConfigServiceFake
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.bitkey.keybox.KeyboxMock
import build.wallet.coroutines.createBackgroundScope
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.recovery.GetDelayNotifyRecoveryStatusF8eClientMock
import build.wallet.f8e.recovery.LostHardwareServerRecoveryMock
import build.wallet.platform.app.AppSessionManagerFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

class RecoverSyncerImplTests : FunSpec({

  val recoveryDao = RecoveryDaoMock(turbines::create)
  val getRecoveryStatusF8eClient = GetDelayNotifyRecoveryStatusF8eClientMock()
  val sessionProvider = AppSessionManagerFake()
  val syncFrequency = 100.milliseconds
  val accountConfigService = AccountConfigServiceFake()
  val recoverySyncer = RecoverySyncerImpl(
    recoveryDao = recoveryDao,
    getRecoveryStatusF8eClient = getRecoveryStatusF8eClient,
    appSessionManager = sessionProvider,
    recoveryLock = RecoveryLock(),
    accountConfigService = accountConfigService
  )

  beforeTest {
    recoveryDao.reset()
    getRecoveryStatusF8eClient.reset()
    sessionProvider.reset()
    accountConfigService.reset()
  }

  test("server polling sync recovery is retrieved (non-null) and cached in dao.") {
    createBackgroundScope().launch {
      recoverySyncer.launchSync(
        fullAccountId = FullAccountId(""),
        scope = this,
        syncFrequency = syncFrequency
      )
    }
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)

    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock
    // emit again after the polling duration
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("server unary recovery sync is retrieved (non-null) and cached in dao.") {
    getRecoveryStatusF8eClient.activeRecovery = LostHardwareServerRecoveryMock

    recoverySyncer.performSync(fullAccountId = FullAccountId(""))

    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }

  test("set local recovery progress") {
    recoverySyncer.setLocalRecoveryProgress(LocalRecoveryAttemptProgress.SweptFunds(KeyboxMock))
    recoveryDao.setLocalRecoveryProgressCalls.awaitItem().shouldBe(Unit)
  }

  test("clear") {
    recoverySyncer.clear()
    recoveryDao.clearCalls.awaitItem().shouldBe(Unit)
  }

  test("recovery syncer doesn't run in the background") {
    sessionProvider.appDidEnterBackground()

    createBackgroundScope().launch {
      recoverySyncer.launchSync(
        scope = this,
        syncFrequency = syncFrequency,
        fullAccountId = FullAccountIdMock
      )
    }

    delay(syncFrequency)
    recoveryDao.setLocalRecoveryProgressCalls.awaitNoEvents()

    sessionProvider.appDidEnterForeground()
    recoveryDao.setActiveServerRecoveryCalls.awaitItem().shouldBe(Unit)
  }
})
