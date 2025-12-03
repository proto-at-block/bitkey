package build.wallet.statemachine.settings.full

import build.wallet.bitkey.keybox.FullAccountMock
import build.wallet.coroutines.turbine.turbines
import build.wallet.feature.FeatureFlagDaoFake
import build.wallet.feature.FeatureFlagValue
import build.wallet.feature.flags.EncryptedDescriptorSupportUploadFeatureFlag
import build.wallet.platform.web.InAppBrowserNavigatorMock
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.root.ActionSuccessDuration
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackFormUiStateMachineImpl
import build.wallet.statemachine.settings.full.feedback.FillingFormBodyModel
import build.wallet.statemachine.ui.awaitBody
import build.wallet.support.*
import build.wallet.time.DateTimeFormatterMock
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlin.time.Duration.Companion.milliseconds

class FeedbackFormUiStateMachineImplTests : FunSpec({

  val supportTicketRepository = SupportTicketRepositoryFake()
  val supportTicketFormValidator = SupportTicketFormValidatorFake()
  val dateTimeFormatter = DateTimeFormatterMock()
  val inAppBrowserNavigator = InAppBrowserNavigatorMock(turbines::create)
  val encryptedDescriptorSupportUploadFeatureFlag = EncryptedDescriptorSupportUploadFeatureFlag(FeatureFlagDaoFake())

  val stateMachine = FeedbackFormUiStateMachineImpl(
    supportTicketRepository = supportTicketRepository,
    supportTicketFormValidator = supportTicketFormValidator,
    dateTimeFormatter = dateTimeFormatter,
    inAppBrowserNavigator = inAppBrowserNavigator,
    actionSuccessDuration = ActionSuccessDuration(0.milliseconds),
    encryptedDescriptorSupportUploadFeatureFlag = encryptedDescriptorSupportUploadFeatureFlag
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

  beforeTest {
    encryptedDescriptorSupportUploadFeatureFlag.reset()
  }

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

  test("feature flag disabled - SendEncryptedDescriptorDataModel and supportRequestedDescriptor picker not shown") {
    encryptedDescriptorSupportUploadFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(false))

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
        hasSupportRequestedDescriptorPicker.shouldBe(false)
      }
    }
  }

  test("feature flag enabled but supportRequestedDescriptor false - SendEncryptedDescriptorDataModel not shown but picker shown") {
    encryptedDescriptorSupportUploadFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

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

  test("feature flag enabled and supportRequestedDescriptor true - SendEncryptedDescriptorDataModel and picker shown") {
    encryptedDescriptorSupportUploadFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

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

  test("feature flag enabled but account is not available - SendEncryptedDescriptorDataModel and supportRequestedDescriptor picker not shown") {
    encryptedDescriptorSupportUploadFeatureFlag.setFlagValue(FeatureFlagValue.BooleanFlag(true))

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
})
