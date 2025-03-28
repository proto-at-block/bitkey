package bitkey.ui.screens.demo

import bitkey.demo.DemoModeServiceFake
import bitkey.ui.framework.test
import bitkey.ui.screens.demo.DemoCodeTrackerScreenId.DEMO_MODE_CODE_SUBMISSION
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.time.MinimumLoadingDuration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.seconds

class DemoCodeEntrySubmissionScreenPresenterTests : FunSpec({
  val demoModeService = DemoModeServiceFake()
  val presenter = DemoCodeEntrySubmissionScreenPresenter(
    minimumLoadingDuration = MinimumLoadingDuration(0.seconds),
    demoModeService = demoModeService
  )

  test("submit code - valid") {
    presenter.test(DemoCodeEntrySubmissionScreen("0000")) { navigator ->
      awaitLoadingScreen(DEMO_MODE_CODE_SUBMISSION)

      navigator.goToCalls.awaitItem().shouldBe(DemoModeEnabledScreen)
    }
  }

  test("submit code - invalid") {
    presenter.test(DemoCodeEntrySubmissionScreen("1111")) { navigator ->
      awaitLoadingScreen(DEMO_MODE_CODE_SUBMISSION)

      navigator.goToCalls.awaitItem().shouldBe(DemoModeCodeEntryScreen)
    }
  }
})
