package bitkey.securitycenter

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.worker.RunStrategy
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class DelayNotifyConfigurationSyncWorkerImplTest : FunSpec({
  val accountService = AccountServiceFake()
  val delayNotifyConfigurationService = DelayNotifyConfigurationServiceFake()
  val syncFrequency = DelayNotifyConfigurationSyncFrequency(30.seconds)
  val worker = DelayNotifyConfigurationSyncWorkerImpl(
    accountService = accountService,
    delayNotifyConfigurationService = delayNotifyConfigurationService,
    delayNotifyConfigurationSyncFrequency = syncFrequency
  )

  beforeTest {
    accountService.reset()
    delayNotifyConfigurationService.reset()
  }

  test("runs on startup and periodically") {
    worker.runStrategy.shouldBe(
      setOf(
        RunStrategy.Startup(),
        RunStrategy.Periodic(interval = syncFrequency.value)
      )
    )
  }

  test("syncs active full account") {
    accountService.setActiveAccount(FullAccountMock)

    worker.executeWork()

    delayNotifyConfigurationService.syncCalls.shouldBe(listOf(FullAccountMock))
  }

  test("does not sync without active full account") {
    worker.executeWork()

    delayNotifyConfigurationService.syncCalls.shouldBeEmpty()
  }
})
