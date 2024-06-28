package build.wallet.coachmark

import build.wallet.account.AccountRepositoryFake
import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.bitkey.keybox.LiteAccountMock
import build.wallet.database.BitkeyDatabaseProviderImpl
import build.wallet.feature.FeatureFlagDaoMock
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.InAppSecurityFeatureFlag
import build.wallet.feature.flags.MultipleFingerprintsIsEnabledFeatureFlag
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
    val inAppSecurityFlag = InAppSecurityFeatureFlag(featureFlagDao)
    val multipleFingerprintsFlag = MultipleFingerprintsIsEnabledFeatureFlag(featureFlagDao)

    beforeTest {
      accountRepository.setActiveAccount(FullAccountMock)
      inAppSecurityFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      multipleFingerprintsFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))
      service = CoachmarkServiceImpl(
        CoachmarkDaoImpl(BitkeyDatabaseProviderImpl(sqlDriver.factory), ClockFake()),
        accountRepository,
        CoachmarkFeatureFlagVisibilityDecider(
          inAppSecurityFeatureFlag = inAppSecurityFlag,
          multipleFingerprintsFeatureFlag = multipleFingerprintsFlag
        ),
        ClockFake()
      )
      service.resetCoachmarks()
    }

    test("coachmarksToDisplay") {
      service
        .coachmarksToDisplay(
          setOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark)
        ).shouldBe(
          Ok(listOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark))
        )
    }

    test("didDisplayCoachmark") {
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.HiddenBalanceCoachmark))
        .shouldBe(Ok(listOf(CoachmarkIdentifier.HiddenBalanceCoachmark)))
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.HiddenBalanceCoachmark)
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.HiddenBalanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("resetCoachmarks") {
      service
        .coachmarksToDisplay(
          setOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark)
        ).shouldBe(
          Ok(listOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark))
        )
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.HiddenBalanceCoachmark)
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.MultipleFingerprintsCoachmark)
      service.markCoachmarkAsDisplayed(CoachmarkIdentifier.BiometricUnlockCoachmark)
      service.resetCoachmarks()
      service
        .coachmarksToDisplay(
          setOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark)
        ).shouldBe(
          Ok(listOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark))
        )
    }

    test("no coachmarks to display for lite accounts") {
      accountRepository.setActiveAccount(LiteAccountMock)
      service
        .coachmarksToDisplay(
          setOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark)
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
        CoachmarkFeatureFlagVisibilityDecider(
          inAppSecurityFeatureFlag = inAppSecurityFlag,
          multipleFingerprintsFeatureFlag = multipleFingerprintsFlag
        ),
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
        CoachmarkFeatureFlagVisibilityDecider(
          inAppSecurityFeatureFlag = inAppSecurityFlag,
          multipleFingerprintsFeatureFlag = multipleFingerprintsFlag
        ),
        ClockFake()
      )
      service
        .coachmarksToDisplay(setOf(CoachmarkIdentifier.HiddenBalanceCoachmark))
        .shouldBe(Ok(emptyList()))
    }

    test("don't return feature flagged coachmarks that are off") {
      inAppSecurityFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      multipleFingerprintsFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))
      service
        .coachmarksToDisplay(
          setOf(CoachmarkIdentifier.HiddenBalanceCoachmark, CoachmarkIdentifier.MultipleFingerprintsCoachmark, CoachmarkIdentifier.BiometricUnlockCoachmark)
        ).shouldBe(
          Ok(emptyList())
        )
    }
  })
