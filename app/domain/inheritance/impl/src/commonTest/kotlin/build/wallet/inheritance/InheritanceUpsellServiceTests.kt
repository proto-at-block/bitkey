package build.wallet.inheritance

import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.onboarding.OnboardingCompletionDaoImpl
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.TestScope
import kotlin.time.Duration.Companion.days

class InheritanceUpsellServiceTests : FunSpec({
  val sqlDriver = inMemorySqlDriver()
  val testScope = TestScope()
  val clock = ClockFake()
  val inheritanceService = InheritanceServiceMock(
    syncCalls = turbines.create("Sync Calls"),
    cancelClaimCalls = turbines.create("Cancel Claim Calls")
  )
  lateinit var dao: InheritanceUpsellViewDao
  lateinit var onboardingDao: OnboardingCompletionDaoImpl
  lateinit var service: InheritanceUpsellServiceImpl

  beforeTest {
    inheritanceService.reset()

    val databaseProvider = BitkeyDatabaseProviderImpl(sqlDriver.factory)

    dao = InheritanceUpsellViewDaoImpl(databaseProvider)
    onboardingDao = OnboardingCompletionDaoImpl(databaseProvider)
    service = InheritanceUpsellServiceImpl(
      coroutineScope = testScope,
      dao = dao,
      onboardingCompletionDao = onboardingDao,
      inheritanceService = inheritanceService,
      clock = clock
    )
  }

  test("initial state shows upsell for existing users without onboarding timestamp") {
    service.shouldShowUpsell().shouldBe(true)
  }

  test("shouldShowUpsell is false immediately after onboarding completion") {
    // Record onboarding completion at current time
    onboardingDao.recordCompletion(
      id = "onboarding_completion",
      timestamp = clock.now()
    )

    service.shouldShowUpsell().shouldBe(false)
  }

  test("shouldShowUpsell is false when upsell has been seen") {
    service.markUpsellAsSeen()
    service.shouldShowUpsell().shouldBe(false)
  }

  test("shouldShowUpsell is false when onboarding was completed less than 2 weeks ago") {
    // Record onboarding completion at current time
    onboardingDao.recordCompletion(
      id = "onboarding_completion",
      timestamp = clock.now()
    )

    // Advance by less than 2 weeks
    clock.advanceBy(13.days)

    service.shouldShowUpsell().shouldBe(false)
  }

  test("shouldShowUpsell is false when inheritance is active") {
    // Set up conditions that would normally show upsell
    onboardingDao.recordCompletion(
      id = "onboarding_completion",
      timestamp = clock.now()
    )
    clock.advanceBy(15.days)

    // But inheritance is active
    inheritanceService.setHasActiveInheritance(true)

    service.shouldShowUpsell().shouldBe(false)
  }

  test("shouldShowUpsell is true when all conditions are met") {
    // Record onboarding completion at current time
    onboardingDao.recordCompletion(
      id = "onboarding_completion",
      timestamp = clock.now()
    )

    // Advance by more than 2 weeks
    clock.advanceBy(15.days)

    service.shouldShowUpsell().shouldBe(true)
  }
})
