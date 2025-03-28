package build.wallet.statemachine.settings.helpcenter

import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.InAppBrowserModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class HelpCenterUiStateMachineImplTests : FunSpec({

  lateinit var stateMachine: HelpCenterUiStateMachineImpl

  val props =
    HelpCenterUiProps(
      onBack = { }
    )

  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  beforeTest {
    stateMachine =
      HelpCenterUiStateMachineImpl(
        inAppBrowserNavigator = inAppBrowserNavigator
      )
  }

  test("should render FAQ in-app browser") {
    stateMachine.test(props) {
      awaitBody<InAppBrowserModel> {
        open()
      }

      inAppBrowserNavigator.onOpenCalls.awaitItem()
        .shouldBe("https://support.bitkey.build/hc")

      inAppBrowserNavigator.onCloseCallback.shouldNotBeNull().invoke()
    }
  }

  // TODO add test for "should open and close FAQ view"
  // TODO add test for "should open and close Contact us view"
})
