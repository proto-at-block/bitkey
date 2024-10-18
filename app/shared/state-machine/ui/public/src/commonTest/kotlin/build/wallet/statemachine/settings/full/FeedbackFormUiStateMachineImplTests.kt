package build.wallet.statemachine.settings.full

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.f8e.F8eEnvironment
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.awaitScreenWithBody
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FillingFormBodyModel
import build.wallet.support.*
import build.wallet.time.ControlledDelayer
import build.wallet.time.DateTimeFormatterMock
import io.kotest.core.spec.style.FunSpec

class FeedbackFormUiStateMachineImplTests : FunSpec({

  val delayer = ControlledDelayer()
  val supportTicketRepository = SupportTicketRepositoryFake()
  val supportTicketFormValidator = SupportTicketFormValidatorFake()
  val dateTimeFormatter = DateTimeFormatterMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)

  val stateMachine = FeedbackFormUiStateMachineImpl(
    delayer = delayer,
    supportTicketRepository = supportTicketRepository,
    supportTicketFormValidator = supportTicketFormValidator,
    dateTimeFormatter = dateTimeFormatter,
    inAppBrowserNavigator = inAppBrowserNavigator
  )

  val props = FeedbackFormUiProps(
    f8eEnvironment = F8eEnvironment.Production,
    accountId = FullAccountIdMock,
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
      awaitScreenWithBody<FillingFormBodyModel>()
    }
  }
})
