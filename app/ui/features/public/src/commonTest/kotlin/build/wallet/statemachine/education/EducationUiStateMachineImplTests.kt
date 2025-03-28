package build.wallet.statemachine.education

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.ui.model.StandardClick
import build.wallet.ui.model.button.ButtonModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe

class EducationUiStateMachineImplTests : FunSpec({

  val onExitCalls = turbines.create<Unit>("onExit calls")
  val onContinueCalls = turbines.create<Unit>("onContinue calls")

  val stateMachine = EducationUiStateMachineImpl()
  val educationItems =
    listOf(
      EducationItem(
        title = "title",
        subtitle = "subtitle",
        primaryButton =
          ButtonModel(
            "primaryButton",
            size = ButtonModel.Size.Footer,
            onClick = StandardClick {}
          ),
        secondaryButton =
          ButtonModel(
            "secondaryButton",
            size = ButtonModel.Size.Footer,
            onClick = StandardClick {}
          )
      ),
      EducationItem(
        title = "title2"
      ),
      EducationItem(
        title = "title3",
        subtitle = "subtitle3"
      )
    )
  val props =
    EducationUiProps(
      items = educationItems,
      onExit = { onExitCalls += Unit },
      onContinue = { onContinueCalls += Unit }
    )

  test("onExit is called when the close button is clicked") {
    stateMachine.test(props) {
      awaitBody<EducationBodyModel> {
        onDismiss()
      }

      onExitCalls.awaitItem()
    }
  }

  test("education screen iterates through items appropriately") {
    stateMachine.test(props) {
      awaitBody<EducationBodyModel> {
        progressPercentage.value.shouldBe(1 / 3f)
        title.shouldBe("title")
        subtitle.shouldBe("subtitle")
        primaryButton.shouldNotBeNull().text.shouldBe("primaryButton")
        secondaryButton.shouldNotBeNull().text.shouldBe("secondaryButton")

        onClick()
      }

      awaitBody<EducationBodyModel> {
        progressPercentage.value.shouldBe(2 / 3f)
        title.shouldBe("title2")
        subtitle.shouldBeNull()
        primaryButton.shouldBeNull()
        secondaryButton.shouldBeNull()

        onClick()
      }

      awaitBody<EducationBodyModel> {
        progressPercentage.value.shouldBe(1f)
        title.shouldBe("title3")
        subtitle.shouldBe("subtitle3")
        primaryButton.shouldBeNull()
        secondaryButton.shouldBeNull()

        onClick()
      }

      onContinueCalls.awaitItem()
    }
  }
})
