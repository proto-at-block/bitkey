package build.wallet.statemachine.settings.full.device.fingerprints

import app.cash.turbine.plusAssign
import build.wallet.coroutines.turbine.turbines
import build.wallet.firmware.EnrolledFingerprints
import build.wallet.firmware.FingerprintHandle
import build.wallet.statemachine.core.Icon
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.ui.awaitSheet
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.clickSecondaryButton
import build.wallet.ui.model.icon.IconImage.LocalImage
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel.IconAccessory
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

class EditingFingerprintUiStateMachineImplTests : FunSpec({
  val stateMachine = EditingFingerprintUiStateMachineImpl()
  val onBackCalls = turbines.create<Unit>("onBack calls")
  val onSaveCalls = turbines.create<FingerprintHandle>("onSave calls")
  val onDeleteFingerprintCalls = turbines.create<FingerprintHandle>("onDeleteFingerprint calls")
  val enrolledFingerprints = EnrolledFingerprints(
    fingerprintHandles = listOf(
      FingerprintHandle(index = 0, label = "Left Thumb"),
      FingerprintHandle(index = 1, label = "Right Thumb")
    )
  )

  val props = EditingFingerprintProps(
    enrolledFingerprints = enrolledFingerprints,
    onBack = { onBackCalls += Unit },
    onSave = { onSaveCalls += it },
    onDeleteFingerprint = { onDeleteFingerprintCalls += it },
    originalFingerprintLabel = "Left Thumb",
    fingerprintToEdit = FingerprintHandle(index = 0, label = "Left Thumb"),
    isExistingFingerprint = true
  )

  test("edit fingerprint label") {
    stateMachine.test(props) {
      // Change the fingerprint label
      awaitSheet<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<FormMainContentModel.TextInput>()
          .fieldModel.onValueChange("Right index", 0..0)
      }

      // Click Save fingerprint
      awaitSheet<FormBodyModel> {
        header.shouldNotBeNull()
          .headline.shouldBe("Manage Left Thumb")

        mainContentList[0]
          .shouldBeInstanceOf<FormMainContentModel.TextInput>()
          .fieldModel.value.shouldBe("Right index")

        clickSecondaryButton()
      }

      // The updated fingerprint handle should be emitted to onSave
      onSaveCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Right index"))
    }
  }

  test("delete fingerprint and confirm") {
    stateMachine.test(props) {
      // Click Delete fingerprint
      awaitSheet<FormBodyModel> {
        clickPrimaryButton()
      }

      // Confirm deletion
      awaitSheet<FormBodyModel> {
        clickPrimaryButton()
      }

      // Deleted fingerprint should be emitted to onDeleteFingerprint
      onDeleteFingerprintCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Left Thumb"))
    }
  }

  test("select delete fingerprint but cancel") {
    stateMachine.test(props) {
      // Click Delete fingerprint
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull()
          .text.shouldBe("Delete fingerprint")
        secondaryButton.shouldNotBeNull()
          .text.shouldBe("Save fingerprint")
        clickPrimaryButton()
      }

      // Cancel the deletion
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull()
          .text.shouldBe("Delete fingerprint")
        secondaryButton.shouldNotBeNull()
          .text.shouldBe("Cancel")
        clickSecondaryButton()
      }

      // Should go back to the first editing screen
      awaitSheet<FormBodyModel> {
        secondaryButton.shouldNotBeNull().text.shouldBe("Save fingerprint")
      }
    }
  }

  test("onBack calls") {
    stateMachine.test(props) {
      awaitSheet<FormBodyModel> {
        toolbar.shouldNotBeNull()
          .leadingAccessory.shouldBeInstanceOf<IconAccessory>().model.apply {
            iconModel.iconImage.shouldBe(LocalImage(icon = Icon.SmallIconX))
            onClick.shouldNotBeNull()
              .invoke()
          }
      }

      onBackCalls.awaitItem().shouldBe(Unit)
    }
  }

  test("editing fingerprint only removes the toolbar and reorders footer buttons in design system v2") {
    stateMachine.test(props) {
      awaitSheet<FormBodyModel> {
        toolbar
          .shouldNotBeNull()
          .leadingAccessory
          .shouldBeInstanceOf<IconAccessory>()

        primaryButton.shouldNotBeNull().text.shouldBe("Delete fingerprint")
        secondaryButton.shouldNotBeNull().text.shouldBe("Save fingerprint")

        val designSystemV2Model = designSystemV2Model.shouldNotBeNull()

        designSystemV2Model.toolbar.shouldBeNull()
        designSystemV2Model.useLegacyToolbarFallback.shouldBe(false)
        designSystemV2Model.primaryButton.shouldNotBeNull().text.shouldBe("Save fingerprint")
        designSystemV2Model.useLegacyPrimaryButtonFallback.shouldBe(false)
        designSystemV2Model.secondaryButton.shouldNotBeNull().text.shouldBe("Delete fingerprint")
        designSystemV2Model.useLegacySecondaryButtonFallback.shouldBe(false)
      }
    }
  }

  test("automation follows the save action for an existing fingerprint") {
    stateMachine.test(props) {
      awaitSheet<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<FormMainContentModel.TextInput>()
          .fieldModel.onValueChange("Right index", 0..0)
      }

      awaitSheet<FormBodyModel> {
        automateNextPrimaryScreen()
      }

      onSaveCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Right index"))
    }
  }

  test("adding a new fingerprint only shows one footer button in design system v2") {
    stateMachine.test(props.copy(isExistingFingerprint = false)) {
      awaitSheet<FormBodyModel> {
        toolbar.shouldNotBeNull()
        primaryButton.shouldBeNull()
        secondaryButton.shouldNotBeNull().text.shouldBe("Start fingerprint")

        val designSystemV2Model = designSystemV2Model.shouldNotBeNull()

        designSystemV2Model.toolbar.shouldBeNull()
        designSystemV2Model.useLegacyToolbarFallback.shouldBe(false)
        designSystemV2Model.primaryButton.shouldNotBeNull().text.shouldBe("Start fingerprint")
        designSystemV2Model.useLegacyPrimaryButtonFallback.shouldBe(false)
        designSystemV2Model.secondaryButton.shouldBeNull()
        designSystemV2Model.useLegacySecondaryButtonFallback.shouldBe(false)
      }
    }
  }

  test("automation follows the start action for a new fingerprint") {
    stateMachine.test(props.copy(isExistingFingerprint = false)) {
      awaitSheet<FormBodyModel> {
        automateNextPrimaryScreen()
      }

      onSaveCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Left Thumb"))
    }
  }

  test("edit fingerprint label for a new fingerprint") {
    stateMachine.test(props.copy(isExistingFingerprint = false)) {
      // Change the fingerprint label
      awaitSheet<FormBodyModel> {
        mainContentList[0]
          .shouldBeInstanceOf<FormMainContentModel.TextInput>()
          .fieldModel.onValueChange("Right thumb", 0..0)
      }

      awaitSheet<FormBodyModel> {
        // The delete button should not be available
        primaryButton.shouldBeNull()

        // Click Start fingerprint
        secondaryButton.shouldNotBeNull().apply {
          text.shouldBe("Start fingerprint")
          onClick.invoke()
        }
      }

      // The fingerprint to enroll should be emitted in onSave
      onSaveCalls.awaitItem()
        .shouldBe(FingerprintHandle(index = 0, label = "Right thumb"))
    }
  }

  test("attempt to delete last fingerprint") {
    stateMachine.test(
      props.copy(
        EnrolledFingerprints(
          fingerprintHandles = listOf(
            FingerprintHandle(index = 0, label = "Left Thumb")
          )
        )
      )
    ) {
      // Click Delete fingerprint
      awaitSheet<FormBodyModel> {
        primaryButton.shouldNotBeNull()
          .text.shouldBe("Delete fingerprint")
        secondaryButton.shouldNotBeNull()
          .text.shouldBe("Save fingerprint")
        clickPrimaryButton()
      }

      awaitSheet<FormBodyModel> {
        // The callout should be visible
        mainContentList[1]
          .shouldBeInstanceOf<FormMainContentModel.Callout>()
          .item.title.shouldBe("At least one fingerprint is required")
      }
    }
  }
})
