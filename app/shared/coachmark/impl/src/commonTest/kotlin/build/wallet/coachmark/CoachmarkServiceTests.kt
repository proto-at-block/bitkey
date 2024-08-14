package build.wallet.coachmark

import build.wallet.account.AccountRepositoryFake
import build.wallet.analytics.events.EventTrackerMock
import build.wallet.analytics.v1.Action
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.CoachmarksGlobalFeatureFlag
import build.wallet.sqldelight.inMemorySqlDriver
import build.wallet.time.ClockFake
import com.github.michaelbull.result.Ok
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.Instant

class CoachmarkServiceTests :
  FunSpec({
    val sqlDriver = inMemorySqlDriver()

    lateinit var service: CoachmarkService
    val featureFlagDao = FeatureFlagDaoMock()
    val accountRepository = AccountRepositoryFake()
    val coachmarksGlobalFlag = CoachmarksGlobalFeatureFlag(featureFlagDao)
    val eventTracker = EventTrackerMock(turbines::create)

    beforeTest {
      accountRepository.setActiveAccount(FullAccountMock)
      service = CoachmarkServiceImpl(
        CoachmarkDaoImpl(BitkeyDatabaseProviderImpl(sqlDriver.factory)),
        accountRepository,
        CoachmarkVisibilityDecider(
          ClockFake()
        ),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake()
      )
      service.resetCoachmarks()
    }

    test("coachmarksToDisplay") {
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.HiddenBalanceCoachmark,
            CoachmarkIdentifier.MultipleFingerprintsCoachmark,
            CoachmarkIdentifier.BiometricUnlockCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.HiddenBalanceCoachmark,
              CoachmarkIdentifier.MultipleFingerprintsCoachmark,
              CoachmarkIdentifier.BiometricUnlockCoachmark
            )
          )
        )
    }

    test("didDisplayCoachmark") {
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.HiddenBalanceCoachmark))
        .shouldBe(Ok(listOf(CoachmarkIdentifier.HiddenBalanceCoachmark)))
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.HiddenBalanceCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_HIDE_BALANCE)
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.HiddenBalanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("resetCoachmarks") {
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.HiddenBalanceCoachmark,
            CoachmarkIdentifier.MultipleFingerprintsCoachmark,
            CoachmarkIdentifier.BiometricUnlockCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.HiddenBalanceCoachmark,
              CoachmarkIdentifier.MultipleFingerprintsCoachmark,
              CoachmarkIdentifier.BiometricUnlockCoachmark
            )
          )
        )
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.HiddenBalanceCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_HIDE_BALANCE)
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.MultipleFingerprintsCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWED_MULTIPLE_FINGERPRINTS)
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.BiometricUnlockCoachmark)
      eventTracker.eventCalls
        .awaitItem()
        .action
        .shouldBe(Action.ACTION_APP_COACHMARK_VIEWIED_BIOMETRIC_UNLOCK)
      service.resetCoachmarks()
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.HiddenBalanceCoachmark,
            CoachmarkIdentifier.MultipleFingerprintsCoachmark,
            CoachmarkIdentifier.BiometricUnlockCoachmark
          )
        ).shouldBe(
          Ok(
            listOf(
              CoachmarkIdentifier.HiddenBalanceCoachmark,
              CoachmarkIdentifier.MultipleFingerprintsCoachmark,
              CoachmarkIdentifier.BiometricUnlockCoachmark
            )
          )
        )
    }

    test("no coachmarks to display for lite accounts") {
      accountRepository.setActiveAccount(LiteAccountMock)
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.HiddenBalanceCoachmark,
            CoachmarkIdentifier.MultipleFingerprintsCoachmark,
            CoachmarkIdentifier.BiometricUnlockCoachmark
          )
        ).shouldBe(
          Ok(emptyList())
        )
    }

    test("don't return expired coachmarks") {
      val coachmarkDao = CoachmarkDaoFake()
      coachmarkDao.insertCoachmark(CoachmarkIdentifier.HiddenBalanceCoachmark, Instant.DISTANT_PAST)
      service = CoachmarkServiceImpl(
        coachmarkDao,
        accountRepository,
        CoachmarkVisibilityDecider(
          ClockFake()
        ),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake()
      )
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.HiddenBalanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("don't return viewed coachmarks") {
      val coachmarkDao = CoachmarkDaoFake()
      coachmarkDao
        .insertCoachmark(CoachmarkIdentifier.HiddenBalanceCoachmark, Instant.DISTANT_FUTURE)
      coachmarkDao.setViewed(CoachmarkIdentifier.HiddenBalanceCoachmark)
      service = CoachmarkServiceImpl(
        coachmarkDao,
        accountRepository,
        CoachmarkVisibilityDecider(
          ClockFake()
        ),
        coachmarksGlobalFlag,
        eventTracker,
        ClockFake()
      )
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.HiddenBalanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("don't return any coachmarks if the global flag is on") {
      coachmarksGlobalFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      service
        .coachmarksToDisplay(
          setOf(
            CoachmarkIdentifier.HiddenBalanceCoachmark,
            CoachmarkIdentifier.MultipleFingerprintsCoachmark,
            CoachmarkIdentifier.BiometricUnlockCoachmark
          )
        ).shouldBe(
          Ok(emptyList())
        )
    }
  })
