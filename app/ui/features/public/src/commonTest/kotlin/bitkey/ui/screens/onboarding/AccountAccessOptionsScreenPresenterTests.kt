package bitkey.ui.screens.onboarding

import bitkey.ui.framework.test
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.statemachine.account.AccountAccessMoreOptionsFormBodyModel
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AccountAccessOptionsScreenPresenterTests : FunSpec({

  val featureFlagDao = FeatureFlagDaoFake()

  beforeTest {
    featureFlagDao.reset()
  }

  test("go back to get started screen") {
    AccountAccessOptionsScreenPresenter().test(AccountAccessOptionsScreen) { navigator ->
      awaitBody<AccountAccessMoreOptionsFormBodyModel> {
        onBack()
      }

      navigator.goToCalls.awaitItem().shouldBe(WelcomeScreen)
    }
  }
})
