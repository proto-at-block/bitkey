package build.wallet.statemachine.settings.full

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.root.ActionSuccessDuration
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FillingFormBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.support.*
import build.wallet.support.SupportTicketError.InvalidEmailAddress
import build.wallet.support.SupportTicketError.NetworkFailure
import build.wallet.time.DateTimeFormatterMock
import com.github.michaelbull.result.Err
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class FeedbackFormUiStateMachineImplTests : FunSpec({

  val supportTicketRepository = SupportTicketRepositoryFake()
  val supportTicketFormValidator = SupportTicketFormValidatorFake()
  val dateTimeFormatter = DateTimeFormatterMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = FeedbackFormUiStateMachineImpl(
    supportTicketRepository = supportTicketRepository,
    supportTicketFormValidator = supportTicketFormValidator,
    dateTimeFormatter = dateTimeFormatter,
    inAppBrowserNavigator = inAppBrowserNavigator,
    actionSuccessDuration = ActionSuccessDuration(0.milliseconds)
  )

  val props = FeedbackFormUiProps(
    account = FullAccountMock,
    formStructure = SupportTicketForm(
      id = 1L,
      fields = emptyList(),
      conditions = OptimizedSupportTicketFieldConditions(emptyMap())
    ),
    initialData = SupportTicketData.Empty,
    onBack = {}
  )

  val propsWithNullAccount = FeedbackFormUiProps(
    account = null,
    formStructure = SupportTicketForm(
      id = 1L,
      fields = emptyList(),
      conditions = OptimizedSupportTicketFieldConditions(emptyMap())
    ),
    initialData = SupportTicketData.Empty,
    onBack = {}
  )

  test("smoke test") {
    stateMachine.test(props) {
      // A silly smoke test to make sure the state machine doesn't crash. INC-3635.
      awaitBody<FillingFormBodyModel>()
    }
  }

  test("smoke test - without account") {
    stateMachine.test(propsWithNullAccount) {
      // A silly smoke test to make sure the state machine doesn't crash. INC-3635.
      awaitBody<FillingFormBodyModel>()
    }
  }

  test("full account - supportRequestedDescriptor false - SendEncryptedDescriptorDataModel not shown but picker shown") {
    stateMachine.test(props) {
      awaitBody<FillingFormBodyModel> {
        val hasEncryptedDescriptorModel = mainContentList.any { content ->
          content is FormMainContentModel.ListGroup &&
            content.listGroupModel.items.any { item ->
              item.title == "Wallet Descriptor"
            }
        }
        hasEncryptedDescriptorModel.shouldBe(false)

        val hasSupportRequestedDescriptorPicker = mainContentList.any { content ->
          content is FormMainContentModel.Picker &&
            content.title == "Has Support requested a wallet descriptor?"
        }
        hasSupportRequestedDescriptorPicker.shouldBe(true)
      }
    }
  }

  test("full account - supportRequestedDescriptor true - SendEncryptedDescriptorDataModel and picker shown") {
    val propsWithSupportRequestedDescriptor = props.copy(
      initialData = buildSupportTicketData {
        sendEncryptedDescriptor = SendEncryptedDescriptor.Selected(FullAccountMock.accountId)
      }
    )

    stateMachine.test(propsWithSupportRequestedDescriptor) {
      awaitBody<FillingFormBodyModel> {
        val hasEncryptedDescriptorModel = mainContentList.any { content ->
          content is FormMainContentModel.ListGroup &&
            content.listGroupModel.items.any { item ->
              item.title == "Wallet Descriptor"
            }
        }
        hasEncryptedDescriptorModel.shouldBe(true)

        val hasSupportRequestedDescriptorPicker = mainContentList.any { content ->
          content is FormMainContentModel.Picker &&
            content.title == "Has Support requested a wallet descriptor?"
        }
        hasSupportRequestedDescriptorPicker.shouldBe(true)
      }
    }
  }

  test("account not available - SendEncryptedDescriptorDataModel and supportRequestedDescriptor picker not shown") {
    stateMachine.test(propsWithNullAccount) {
      awaitBody<FillingFormBodyModel> {
        val hasEncryptedDescriptorModel = mainContentList.any { content ->
          content is FormMainContentModel.ListGroup &&
            content.listGroupModel.items.any { item ->
              item.title == "Wallet Descriptor"
            }
        }
        hasEncryptedDescriptorModel.shouldBe(false)

        val hasSupportRequestedDescriptorPicker = mainContentList.any { content ->
          content is FormMainContentModel.Picker &&
            content.title == "Has Support requested a wallet descriptor?"
        }
        hasSupportRequestedDescriptorPicker.shouldBe(false)
      }
    }
  }

  test("submit error - invalid email shows error screen with correct message") {
    supportTicketRepository.createTicketResult = Err(InvalidEmailAddress)

    stateMachine.test(props) {
      awaitBody<FillingFormBodyModel> {
        // Trigger form submission
        onSubmitData(formData)
      }

      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("The entered email is not valid.")
        header?.sublineModel?.string.shouldBe("Please provide a different email.")
        primaryButton?.text.shouldBe("Dismiss")
      }
    }
  }

  test("submit error - network failure shows error screen with correct message") {
    supportTicketRepository.createTicketResult =
      Err(NetworkFailure(cause = Error("Connection timeout")))

    stateMachine.test(props) {
      awaitBody<FillingFormBodyModel> {
        // Trigger form submission
        onSubmitData(formData)
      }

      awaitUntilBody<FormBodyModel> {
        header?.headline.shouldBe("Couldn't submit your feedback")
        header?.sublineModel?.string.shouldBe("We couldn't submit your feedback. Please try again later.")
        primaryButton?.text.shouldBe("Retry")
        secondaryButton?.text.shouldBe("Dismiss")
      }
    }
  }
})
