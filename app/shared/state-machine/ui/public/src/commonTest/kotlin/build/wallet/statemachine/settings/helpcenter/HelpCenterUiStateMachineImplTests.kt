package build.wallet.statemachine.settings.helpcenter

import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId.SETTINGS_HELP_CENTER
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.ListGroup
import build.wallet.statemachine.core.test
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

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

  test("should raise SETTING_HELP_CENTER eventTracker event") {
    stateMachine.test(props) {
      with(awaitItem()) {
        body.eventTrackerScreenInfo
          .shouldNotBeNull()
          .eventTrackerScreenId
          .shouldBeEqual(SETTINGS_HELP_CENTER)
      }
    }
  }

  test("should render Help Center with FAQ and Contact us") {
    stateMachine.test(props) {
      awaitScreenWithBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<ListGroup>()) {
          with(listGroupModel.items[0]) {
            title.shouldBe("FAQ")
            secondaryText.shouldBeNull()
            leadingAccessory.shouldNotBeNull()
            trailingAccessory.shouldNotBeNull()
          }
          with(listGroupModel.items[1]) {
            title.shouldBe("Contact us")
            secondaryText.shouldBeNull()
            leadingAccessory.shouldNotBeNull()
            trailingAccessory.shouldNotBeNull()
          }
        }
      }
    }
  }

  // TODO add test for "should open and close FAQ view"
  // TODO add test for "should open and close Contact us view"
})
