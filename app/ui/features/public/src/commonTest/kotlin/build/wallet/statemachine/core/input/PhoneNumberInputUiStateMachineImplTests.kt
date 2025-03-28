package build.wallet.statemachine.core.input

import app.cash.turbine.plusAssign
import bitkey.f8e.error.F8eError
import bitkey.f8e.error.SpecificClientErrorMock
import bitkey.f8e.error.code.AddTouchpointClientErrorCode
import build.wallet.coroutines.turbine.turbines
import build.wallet.phonenumber.PhoneNumber
import build.wallet.phonenumber.PhoneNumberFormatterMock
import build.wallet.phonenumber.PhoneNumberMock
import build.wallet.phonenumber.PhoneNumberValidatorMock
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel.TextInput
import build.wallet.statemachine.core.input.DataInputStyle.Edit
import build.wallet.statemachine.core.input.DataInputStyle.Enter
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.statemachine.ui.matchers.shouldBeDisabled
import build.wallet.statemachine.ui.matchers.shouldBeEnabled
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.ButtonAccessory
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class PhoneNumberInputUiStateMachineImplTests : FunSpec({

  data class SubmitPhoneNumberParams(
    val phoneNumber: PhoneNumber,
    val errorCallback: (F8eError<AddTouchpointClientErrorCode>) -> Unit,
  )

  val onCloseCalls = turbines.create<Unit>("on close calls")
  val onSubmitPhoneNumberCalls =
    turbines.create<SubmitPhoneNumberParams>(
      "on phone number entered calls"
    )
  val onSkipCalls = turbines.create<Unit>("on skip calls")

  val props =
    PhoneNumberInputUiProps(
      dataInputStyle = Enter,
      prefillValue = null,
      subline = null,
      onClose = { onCloseCalls.add(Unit) },
      onSubmitPhoneNumber = { number, errorCallback ->
        onSubmitPhoneNumberCalls.add(SubmitPhoneNumberParams(number, errorCallback))
      },
      primaryButtonText = "Skip for Now",
      primaryButtonOnClick = { onSkipCalls += Unit },
      secondaryButtonText = "Use Different Country Number",
      secondaryButtonOnClick = null,
      skipBottomSheetProvider = { SheetModelMock(it) }
    )

  val phoneNumberFormatter = PhoneNumberFormatterMock()
  val phoneNumberValidator = PhoneNumberValidatorMock()
  val stateMachine =
    PhoneNumberInputUiStateMachineImpl(
      phoneNumberFormatter = phoneNumberFormatter,
      phoneNumberValidator = phoneNumberValidator
    )

  beforeTest {
    phoneNumberFormatter.reset()
    phoneNumberValidator.reset()
  }

  test("initial state - example number") {
    phoneNumberValidator.dialingCodeForCurrentRegion = 3
    phoneNumberValidator.exampleFormattedNumberForCurrentRegion = "+0 11-11-11"
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Enter your phone number")
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.placeholderText.shouldBe("+0 11-11-11")
          fieldModel.value.shouldBe("+3 ")
        }
      }
    }
  }

  test("initial state - no example number") {
    phoneNumberValidator.dialingCodeForCurrentRegion = 4
    phoneNumberValidator.exampleFormattedNumberForCurrentRegion = null
    stateMachine.test(props.copy(dataInputStyle = Edit)) {
      awaitBody<FormBodyModel> {
        header.shouldNotBeNull().headline.shouldBe("Edit your phone number")
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.placeholderText.shouldBe("Phone number")
          fieldModel.value.shouldBe("+4 ")
        }
      }
    }
  }

  test("close") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        toolbar.shouldNotBeNull()
          .leadingAccessory.shouldNotBeNull().shouldBeInstanceOf<IconAccessory>()
          .model.onClick.shouldNotBeNull().invoke()
        onCloseCalls.awaitItem()
      }
    }
  }

  test("skip sheet - go back") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        toolbar.shouldNotBeNull()
          .trailingAccessory.shouldNotBeNull().shouldBeInstanceOf<ButtonAccessory>()
          .model.onClick.shouldNotBeNull().invoke()
      }

      awaitItem().bottomSheetModel
        .shouldNotBeNull()
        .onClosed()

      awaitItem().bottomSheetModel
        .shouldBeNull()
    }
  }

  test("skipBottomSheetProvider passed as null") {
    stateMachine.test(props.copy(skipBottomSheetProvider = null)) {
      awaitBody<FormBodyModel> {
        toolbar.shouldNotBeNull()
          .trailingAccessory.shouldBeNull()
      }
    }
  }

  test("national number onValueChange invalid number") {
    val newValue = "123456"
    phoneNumberFormatter.formatPartialPhoneNumberResult = "1-23-45-6"
    phoneNumberValidator.validatePhoneNumberResult = null
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          primaryButton.shouldNotBeNull().shouldBeDisabled()
          fieldModel.onValueChange(newValue)
        }
      }
      awaitBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(phoneNumberFormatter.formatPartialPhoneNumberResult)
          primaryButton.shouldNotBeNull().shouldBeDisabled()
        }
      }
    }
  }

  test("national number onValueChange valid number") {
    val newValue = "123456"
    phoneNumberFormatter.formatPartialPhoneNumberResult = "1-23-45-6"
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          primaryButton.shouldNotBeNull().shouldBeDisabled()
          phoneNumberValidator.validatePhoneNumberResult = PhoneNumberMock
          fieldModel.onValueChange(newValue)
        }
      }

      awaitBody<FormBodyModel> {
        with(mainContentList.first().shouldBeInstanceOf<TextInput>()) {
          fieldModel.value.shouldBe(PhoneNumberMock.formattedDisplayValue)
          primaryButton.shouldNotBeNull().shouldBeEnabled()
        }
      }
    }
  }

  test("error - touchpoint already active") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        phoneNumberFormatter.formatPartialPhoneNumberResult = "1-23-45-6"
        phoneNumberValidator.validatePhoneNumberResult = PhoneNumberMock
        mainContentList.first().shouldBeInstanceOf<TextInput>()
          .fieldModel.onValueChange("abc")
      }

      // Ready to submit
      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        body.shouldBeInstanceOf<FormBodyModel>()
          .clickPrimaryButton()
      }

      // Submitting
      awaitBody<FormBodyModel>()

      // Send error in the callback
      val submitPhoneNumber = onSubmitPhoneNumberCalls.awaitItem()
      submitPhoneNumber.errorCallback(
        SpecificClientErrorMock(AddTouchpointClientErrorCode.TOUCHPOINT_ALREADY_ACTIVE)
      )

      // Showing error sheet
      val errorModel =
        awaitItem().bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
      with(errorModel.header.shouldNotBeNull()) {
        headline.shouldBe(
          "The entered phone number is already registered for notifications on this account."
        )
        sublineModel.shouldNotBeNull().string.shouldBe("Please provide a different phone number.")
      }
      errorModel.onBack.shouldNotBeNull().invoke()

      // Error sheet closed
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("error - unsupported country code - skip") {
    stateMachine.test(props) {
      awaitBody<FormBodyModel> {
        phoneNumberFormatter.formatPartialPhoneNumberResult = "1-23-45-6"
        phoneNumberValidator.validatePhoneNumberResult = PhoneNumberMock
        mainContentList.first().shouldBeInstanceOf<TextInput>()
          .fieldModel.onValueChange("abc")
      }

      // Ready to submit
      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        body.shouldBeInstanceOf<FormBodyModel>()
          .clickPrimaryButton()
      }

      // Submitting
      awaitBody<FormBodyModel>()

      // Send error in the callback
      val submitPhoneNumber = onSubmitPhoneNumberCalls.awaitItem()
      submitPhoneNumber.errorCallback(
        SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE)
      )

      // Showing error sheet
      val errorModel =
        awaitItem().bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
      with(errorModel.header.shouldNotBeNull()) {
        headline.shouldBe("SMS notifications are not available in your country")
        sublineModel.shouldNotBeNull().string.shouldBe(
          "SMS notifications are currently not supported for this country."
        )
      }
      errorModel.clickPrimaryButton()
      onSkipCalls.awaitItem()

      // Close error sheet
      errorModel.clickSecondaryButton()
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }

  test("error - unsupported country code - not skippable") {
    stateMachine.test(
      props.copy(
        primaryButtonText = "Got it",
        primaryButtonOnClick = null,
        secondaryButtonText = null,
        secondaryButtonOnClick = null
      )
    ) {
      awaitBody<FormBodyModel> {
        phoneNumberFormatter.formatPartialPhoneNumberResult = "1-23-45-6"
        phoneNumberValidator.validatePhoneNumberResult = PhoneNumberMock
        mainContentList.first().shouldBeInstanceOf<TextInput>()
          .fieldModel.onValueChange("abc")
      }

      // Ready to submit
      with(awaitItem()) {
        bottomSheetModel.shouldBeNull()
        body.shouldBeInstanceOf<FormBodyModel>()
          .clickPrimaryButton()
      }

      // Submitting
      awaitBody<FormBodyModel>()

      // Send error in the callback
      val submitPhoneNumber = onSubmitPhoneNumberCalls.awaitItem()
      submitPhoneNumber.errorCallback(
        SpecificClientErrorMock(AddTouchpointClientErrorCode.UNSUPPORTED_COUNTRY_CODE)
      )

      // Showing error sheet
      val errorModel =
        awaitItem().bottomSheetModel.shouldNotBeNull()
          .body.shouldBeInstanceOf<FormBodyModel>()
      with(errorModel.header.shouldNotBeNull()) {
        headline.shouldBe("SMS notifications are not available in your country")
        sublineModel.shouldNotBeNull().string.shouldBe(
          "SMS notifications are currently not supported for this country."
        )
      }
      errorModel.secondaryButton.shouldBeNull()
      errorModel.clickPrimaryButton()

      // Error sheet closed
      awaitItem().bottomSheetModel.shouldBeNull()
    }
  }
})
