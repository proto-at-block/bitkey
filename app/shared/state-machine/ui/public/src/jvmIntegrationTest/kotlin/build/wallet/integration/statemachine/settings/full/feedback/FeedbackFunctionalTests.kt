package build.wallet.integration.statemachine.settings.full.feedback

import build.wallet.bitkey.account.FullAccount
import build.wallet.coroutines.turbine.turbines
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.StateMachineTester
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.settings.full.feedback.FeedbackEventTrackerScreenId
import build.wallet.statemachine.settings.full.feedback.FeedbackUiProps
import build.wallet.statemachine.settings.full.feedback.FeedbackUiStateMachine
import build.wallet.statemachine.ui.awaitUntilScreenModelWithBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.support.SupportTicketField
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.ui.model.input.TextFieldModel
import build.wallet.ui.model.picker.ItemPickerModel
import io.kotest.assertions.forEachAsClue
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEmpty
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.collections.immutable.ImmutableList

class FeedbackFunctionalTests : FunSpec({

  val onBackCalls = turbines.create<Unit>("onBack")

  lateinit var account: FullAccount
  lateinit var feedbackStateMachine: FeedbackUiStateMachine
  beforeTest {
    val appTester = launchNewApp()
    account = appTester.onboardFullAccountWithFakeHardware()
    feedbackStateMachine = appTester.app.feedbackUiStateMachine
  }

  test("feedback form loads and closes") {
    feedbackStateMachine.test(
      props =
        FeedbackUiProps(
          f8eEnvironment = account.config.f8eEnvironment,
          accountId = account.accountId,
          onBack = { onBackCalls.add(Unit) }
        ),
      useVirtualTime = false
    ) {
      awaitFeedbackFilling {
        withClue("primary button") {
          primaryButton.shouldNotBeNull()
        }

        val email =
          mainContentList
            .shouldContainEmailField()

        email.value.shouldBeEmpty()

        withClue("onBack") {
          onBack.shouldNotBeNull()
        }.invoke()
        onBackCalls.awaitItem()
      }
    }
  }

  test("feedback form doesn't close when modified") {
    feedbackStateMachine.test(
      props =
        FeedbackUiProps(
          f8eEnvironment = account.config.f8eEnvironment,
          accountId = account.accountId,
          onBack = { onBackCalls.add(Unit) }
        ),
      useVirtualTime = false
    ) {
      withClue("Empty form, filling email") {
        awaitFeedbackFilling {
          mainContentList
            .shouldContainEmailField()
            .onValueChange(
              "anything",
              IntRange.EMPTY
            )
        }
      }

      withClue("Form with some email value, leaving 1") {
        awaitFeedbackFilling {
          withClue("alertModel") {
            screen.alertModel.shouldBeNull()
          }

          onBack.shouldNotBeNull()
            .invoke()
        }
      }

      withClue("Dialog asking user to confirm leave, staying") {
        awaitFeedbackFilling {
          val alert =
            withClue("alertModel") {
              screen.alertModel.shouldNotBeNull()
            }
          alert.title shouldBe "Are you sure you want to leave?"

          alert.secondaryButtonText shouldBe "Stay"
          withClue("onSecondaryButtonClick") {
            alert.onSecondaryButtonClick.shouldNotBeNull()
          }.invoke()
        }
      }

      withClue("Form with some email value, leaving 2") {
        awaitFeedbackFilling {
          withClue("alertModel") {
            screen.alertModel.shouldBeNull()
          }

          onBack.shouldNotBeNull()
            .invoke()
        }
      }

      withClue("Dialog asking user to confirm leave, leaving") {
        awaitFeedbackFilling {
          val alert =
            withClue("alertModel") {
              screen.alertModel.shouldNotBeNull()
            }
          alert.title shouldBe "Are you sure you want to leave?"
          alert.primaryButtonText shouldBe "Leave"
          alert.onPrimaryButtonClick.invoke()

          onBackCalls.awaitItem()
        }
      }
    }
  }

  test("feedback form submits successfully after filled") {
    feedbackStateMachine.test(
      props =
        FeedbackUiProps(
          f8eEnvironment = account.config.f8eEnvironment,
          accountId = account.accountId,
          onBack = { onBackCalls.add(Unit) }
        ),
      useVirtualTime = false
    ) {
      fillForm(
        beforeEach = {
          withClue("primary button") {
            val primary = primaryButton.shouldNotBeNull()
            primary.isEnabled.shouldBeFalse()
          }
        },
        afterLast = {
          withClue("primary button") {
            val primary = primaryButton.shouldNotBeNull()
            primary.isEnabled.shouldBeTrue()
            primary.onClick.invoke()
          }
        }
      )

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(
        id = FeedbackEventTrackerScreenId.FEEDBACK_SUBMITTING
      ) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      awaitUntilScreenWithBody<FormBodyModel>(
        id = FeedbackEventTrackerScreenId.FEEDBACK_SUBMIT_SUCCESS
      )

      onBackCalls.awaitItem()
    }
  }

  test("subcategory picker shows after choosing category") {
    feedbackStateMachine.test(
      props =
        FeedbackUiProps(
          f8eEnvironment = account.config.f8eEnvironment,
          accountId = account.accountId,
          onBack = { onBackCalls.add(Unit) }
        ),
      useVirtualTime = false
    ) {
      awaitFeedbackFilling {
        val categoryPicker =
          form.mainContentList
            .shouldContainCategoryPicker()

        val ordersAndShipping = categoryPicker.options.elementAtOrNull(1).shouldNotBeNull()
        ordersAndShipping.title shouldBe "Orders & Shipping"

        categoryPicker.onOptionSelected(ordersAndShipping)
      }

      awaitFeedbackFilling {
        form.mainContentList
          .shouldContainSubcategoryPicker("Order & Shipping (required)")
      }
    }
  }
}) {
  private companion object {
    private object FieldIndices {
      const val EMAIL = 0
      const val COUNTRY = 1
      const val CATEGORY = 2
      const val SUBCATEGORY = 3
      const val SUBJECT = -5
      const val DESCRIPTION = -4
    }

    data class FormFillingContext(
      val screen: ScreenModel,
      val form: FormBodyModel,
    ) {
      val primaryButton by form::primaryButton
      val onBack by form::onBack
      val mainContentList by form::mainContentList
    }

    suspend inline fun StateMachineTester<FeedbackUiProps, ScreenModel>.fillForm(
      beforeEach: FormFillingContext.() -> Unit = {},
      afterLast: FormFillingContext.() -> Unit = {},
    ) {
      awaitFeedbackFilling {
        withClue("before email") {
          beforeEach()
        }

        mainContentList
          .shouldContainEmailField()
          .onValueChange("valid@example.com", IntRange.EMPTY)
      }

      awaitFeedbackFilling {
        withClue("before country") {
          beforeEach()
        }

        val countryPicker =
          mainContentList
            .shouldContainCountryPicker()

        countryPicker.onOptionSelected(countryPicker.options.first())
      }

      awaitFeedbackFilling {
        withClue("before category") {
          beforeEach()
        }

        val categoryPicker =
          mainContentList
            .shouldContainCategoryPicker()

        // We know that this category has no subcategories
        val firstItem = categoryPicker.options.first()
        firstItem.title shouldBe "Feedback & Suggestions"

        categoryPicker.onOptionSelected(firstItem)
      }

      awaitFeedbackFilling {
        withClue("before subject") {
          beforeEach()
        }

        mainContentList
          .shouldContainSubjectField()
          .onValueChange("Example subject", IntRange.EMPTY)
      }

      awaitFeedbackFilling {
        withClue("before description") {
          beforeEach()
        }

        mainContentList
          .shouldContainDescriptionArea()
          .onValueChange("Example description", IntRange.EMPTY)
      }

      awaitFeedbackFilling {
        withClue("after filled") {
          afterLast()
        }
      }
    }

    suspend inline fun StateMachineTester<FeedbackUiProps, ScreenModel>.awaitFeedbackFilling(
      block: FormFillingContext.() -> Unit = { },
    ): ScreenModel {
      return awaitUntilScreenModelWithBody<FormBodyModel>(
        id = FeedbackEventTrackerScreenId.FEEDBACK_FILLING_FORM
      ) {
        val context =
          FormFillingContext(
            screen = this,
            form = body as FormBodyModel
          )
        context.block()
      }
    }

    fun ImmutableList<FormMainContentModel>.shouldContainEmailField(): TextFieldModel {
      return withClue("email field") {
        val field = shouldContainField<FormMainContentModel.TextInput>(FieldIndices.EMAIL)

        field.title shouldBe "Email (required)"
        field.fieldModel.keyboardType shouldBe TextFieldModel.KeyboardType.Email

        field.fieldModel
      }
    }

    fun ImmutableList<FormMainContentModel>.shouldContainCountryPicker(): ItemPickerModel<SupportTicketField.Picker.Item> {
      return withClue("country field") {
        val picker = shouldContainField<FormMainContentModel.Picker>(FieldIndices.COUNTRY)

        picker.title shouldBe "Where are you located? (required)"
        picker.fieldModel.options.shouldNotBeEmpty()
        picker.fieldModel.options.forEachAsClue {
          it.shouldBeInstanceOf<SupportTicketField.Picker.Item>()
        }

        @Suppress("UNCHECKED_CAST")
        picker.fieldModel as ItemPickerModel<SupportTicketField.Picker.Item>
      }
    }

    fun ImmutableList<FormMainContentModel>.shouldContainCategoryPicker(): ItemPickerModel<SupportTicketField.Picker.Item> {
      return withClue("category field") {
        val picker = shouldContainField<FormMainContentModel.Picker>(FieldIndices.CATEGORY)

        picker.title shouldBe "How can we help? (required)"
        picker.fieldModel.options.shouldNotBeEmpty()
        picker.fieldModel.options.forEachAsClue {
          it.shouldBeInstanceOf<SupportTicketField.Picker.Item>()
        }

        @Suppress("UNCHECKED_CAST")
        picker.fieldModel as ItemPickerModel<SupportTicketField.Picker.Item>
      }
    }

    fun ImmutableList<FormMainContentModel>.shouldContainSubcategoryPicker(
      title: String,
    ): ItemPickerModel<SupportTicketField.Picker.Item> {
      return withClue("subcategory field - $title") {
        val picker = shouldContainField<FormMainContentModel.Picker>(FieldIndices.SUBCATEGORY)

        picker.title shouldBe title
        picker.fieldModel.options.shouldNotBeEmpty()
        picker.fieldModel.options.forEachAsClue {
          it.shouldBeInstanceOf<SupportTicketField.Picker.Item>()
        }

        @Suppress("UNCHECKED_CAST")
        picker.fieldModel as ItemPickerModel<SupportTicketField.Picker.Item>
      }
    }

    fun ImmutableList<FormMainContentModel>.shouldContainSubjectField(): TextFieldModel {
      return withClue("subject field") {
        val field = shouldContainField<FormMainContentModel.TextInput>(FieldIndices.SUBJECT)

        field.title shouldBe "Subject (required)"
        field.fieldModel.keyboardType shouldBe TextFieldModel.KeyboardType.Default

        field.fieldModel
      }
    }

    fun ImmutableList<FormMainContentModel>.shouldContainDescriptionArea(): TextFieldModel {
      return withClue("description area") {
        val area = shouldContainField<FormMainContentModel.TextArea>(FieldIndices.DESCRIPTION)

        area.title shouldBe "Description (required)"
        area.fieldModel.keyboardType shouldBe TextFieldModel.KeyboardType.Default

        area.fieldModel
      }
    }

    inline fun <reified Field : FormMainContentModel> ImmutableList<FormMainContentModel>.shouldContainField(
      index: Int,
    ): Field {
      return withClue("field at $index") {
        this.elementAtIndexOrNegativeIndexOrNull(index)
          .shouldNotBeNull()
          .shouldBeInstanceOf<Field>()
      }
    }

    private fun <T> List<T>.elementAtIndexOrNegativeIndexOrNull(indexOrNegativeIndex: Int): T? {
      return if (indexOrNegativeIndex >= 0) {
        elementAtOrNull(indexOrNegativeIndex)
      } else {
        elementAtOrNull(size + indexOrNegativeIndex)
      }
    }
  }
}
