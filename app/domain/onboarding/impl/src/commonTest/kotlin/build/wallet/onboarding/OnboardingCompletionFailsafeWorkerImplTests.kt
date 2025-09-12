package build.wallet.onboarding

import build.wallet.account.AccountServiceFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.turbine.awaitNoEvents
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.f8e.onboarding.OnboardingF8eClientMock
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.OnboardingCompletionFailsafeFeatureFlag
import build.wallet.feature.isEnabled
import build.wallet.feature.setFlagValue
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.testing.shouldBeOk
import build.wallet.time.ClockFake
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlin.time.Duration.Companion.milliseconds

@OptIn(DelicateCoroutinesApi::class)
class OnboardingCompletionFailsafeWorkerImplTests : FunSpec({

  val accountService = AccountServiceFake()
  val featureFlagDao = FeatureFlagDaoFake()
  val sqlDriver = inMemorySqlDriver()
  val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)

  val onboardingCompletionFailsafeFeatureFlag = OnboardingCompletionFailsafeFeatureFlag(
    featureFlagDao = featureFlagDao
  )
  val onboardingF8eClient = OnboardingF8eClientMock(turbines::create)
  val clock = ClockFake()
  val onboardingCompletionDao = OnboardingCompletionDaoImpl(
    databaseProvider = databaseProvider
  )
  val onboardingCompletionService = OnboardingCompletionServiceImpl(
    clock = clock,
    onboardingCompletionDao = onboardingCompletionDao
  )

  val worker = OnboardingCompletionFailsafeWorkerImpl(
    accountService = accountService,
    onboardingCompletionService = onboardingCompletionService,
    onboardingCompletionFailsafeFeatureFlag = onboardingCompletionFailsafeFeatureFlag,
    onboardingF8eClient = onboardingF8eClient
  )

  beforeTest {
    accountService.reset()
    onboardingCompletionFailsafeFeatureFlag.reset()
    onboardingCompletionDao.clearCompletionTimestamp()
  }

  test("completeOnboarding is not called when completion timestamp is set") {
    runTest {
      accountService.setActiveAccount(FullAccountMock)
      onboardingCompletionFailsafeFeatureFlag.setFlagValue(true)
      onboardingCompletionDao.recordCompletion(
        id = onboardingCompletionDao.fallbackKeyId,
        timestamp = clock.now
      )

      worker.executeWork()

      // Verify the precondition that should prevent the call
      onboardingCompletionFailsafeFeatureFlag.isEnabled().shouldBe(true)

      // Reduce arbitrary delay from awaitNoEvents from 50ms to 10ms, ensure nothing is fired
      onboardingF8eClient.completeOnboardingCalls.awaitNoEvents(timeout = 10.milliseconds)
    }
  }

  test("completeOnboarding is not called when flag is disabled") {
    runTest {
      accountService.setActiveAccount(FullAccountMock)
      onboardingCompletionFailsafeFeatureFlag.setFlagValue(false)

      worker.executeWork()

      // Verify the precondition that should prevent the call
      onboardingCompletionFailsafeFeatureFlag.isEnabled().shouldBe(false)

      // Reduce arbitrary delay from awaitNoEvents from 50ms to 10ms, ensure nothing is fired
      onboardingF8eClient.completeOnboardingCalls.awaitNoEvents(timeout = 10.milliseconds)
      onboardingCompletionDao.getCompletionTimestamp(
        id = onboardingCompletionDao.fallbackKeyId
      ).get().shouldBeNull()
    }
  }

  test("completeOnboarding is not called when account is not full account") {
    runTest {
      accountService.setActiveAccount(LiteAccountMock)
      onboardingCompletionFailsafeFeatureFlag.setFlagValue(true)

      worker.executeWork()

      // Use awaitNoEvents with explicit timeout
      onboardingF8eClient.completeOnboardingCalls.awaitNoEvents(timeout = 10.milliseconds)
      onboardingCompletionDao.getCompletionTimestamp(
        id = onboardingCompletionDao.fallbackKeyId
      ).get().shouldBeNull()
    }
  }

  test("completeOnboarding is called when flag is enabled") {
    runTest {
      accountService.setActiveAccount(FullAccountMock)
      onboardingCompletionFailsafeFeatureFlag.setFlagValue(true)

      worker.executeWork()

      // Verify the call was made - direct awaitItem for mock verification
      onboardingF8eClient.completeOnboardingCalls.awaitItem()
      onboardingCompletionDao.getCompletionTimestamp(
        id = onboardingCompletionDao.fallbackKeyId
      ).shouldBeOk(clock.now)
    }
  }
})
