package build.wallet.statemachine.settings.full

import build.wallet.bitkey.f8e.FullAccountIdMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.testWithVirtualTime
import build.wallet.statemachine.root.ActionSuccessDuration
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FillingFormBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.support.*
import build.wallet.time.DateTimeFormatterMock
import io.kotest.core.spec.style.FunSpec
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
    stateMachine.testWithVirtualTime(props) {
      // A silly smoke test to make sure the state machine doesn't crash. INC-3635.
      awaitBody<FillingFormBodyModel>()
    }
  }
})
