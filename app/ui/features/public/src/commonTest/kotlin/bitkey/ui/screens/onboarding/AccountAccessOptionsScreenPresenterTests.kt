package bitkey.ui.screens.onboarding

import bitkey.ui.framework.test
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.flags.InheritanceFeatureFlag
import build.wallet.feature.setFlagValue
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe

class AccountAccessOptionsScreenPresenterTests : FunSpec({

  val featureFlagDao = FeatureFlagDaoFake()
  val inheritanceFlag = InheritanceFeatureFlag(featureFlagDao)

  beforeTest {
    featureFlagDao.reset()
  }

  test("go back to get started screen") {
    AccountAccessOptionsScreenPresenter(inheritanceFlag).test(AccountAccessOptionsScreen) { navigator ->
      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        onBack()
      }

      navigator.goToCalls.awaitItem().shouldBe(WelcomeScreen)
    }
  }

  test("inheritance feature flag is off") {
    inheritanceFlag.setFlagValue(false)

    AccountAccessOptionsScreenPresenter(inheritanceFlag).test(AccountAccessOptionsScreen) {
      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        isInheritanceEnabled.shouldBeFalse()
      }
    }
  }

  test("inheritance feature flag is on") {
    inheritanceFlag.setFlagValue(true)

    AccountAccessOptionsScreenPresenter(inheritanceFlag).test(AccountAccessOptionsScreen) {
      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        isInheritanceEnabled.shouldBeTrue()
      }
    }
  }
})
