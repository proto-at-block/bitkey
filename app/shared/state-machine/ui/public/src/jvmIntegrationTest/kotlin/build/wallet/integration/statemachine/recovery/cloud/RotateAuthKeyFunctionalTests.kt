package build.wallet.integration.statemachine.recovery.cloud

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.plusAssign
import build.wallet.analytics.events.screen.context.AuthKeyRotationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.coroutines.turbine.turbines
import build.wallet.relationships.syncAndVerifyRelationships
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf

class RotateAuthKeyFunctionalTests : FunSpec({
  test("Proposed rotation flag is persisted") {
    val firstAppRun = launchNewApp()
    firstAppRun.onboardFullAccountWithFakeHardware()

    firstAppRun.app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    firstAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }
    }

    val secondAppRun = firstAppRun.relaunchApp()

    secondAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }
    }
  }

  test("User can clear proposed rotation flag") {
    val firstAppRun = launchNewApp()
    firstAppRun.onboardFullAccountWithFakeHardware()

    firstAppRun.app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    firstAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION

        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    val secondAppRun = firstAppRun.relaunchApp()

    secondAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("User can successfully rotate keys from proposal") {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()

    // Auth key rotation depends on cloud backup upload, which requires SocRec relationships to be
    // synced up, even if we don't have any.
    app.app.appComponent.relationshipsService.syncAndVerifyRelationships(account)

    app.app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    app.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.secondaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }

      awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("User can successfully rotate keys from settings").config(
    coroutineTestScope = true
  ) {
    val app = launchNewApp()
    val account = app.onboardFullAccountWithFakeHardware()

    // Auth key rotation depends on cloud backup upload, which requires SocRec relationships to be
    // synced up, even if we don't have any.
    app.app.appComponent.relationshipsService.syncAndVerifyRelationships(account)

    val onBackCalls = turbines.create<Unit>("onBackCalls")

    val props = RotateAuthKeyUIStateMachineProps(
      account = account,
      origin = RotateAuthKeyUIOrigin.Settings(
        onBack = {
          onBackCalls += Unit
        }
      )
    )
    app.app.rotateAuthUIStateMachine.test(props) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }

      awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      onBackCalls.awaitItem()
    }
  }

  test("Key rotation resumes from previous attempt") {
    val firstAppRun = launchNewApp()
    val account = firstAppRun.onboardFullAccountWithFakeHardware()

    // Auth key rotation depends on cloud backup upload, which requires SocRec relationships to be
    // synced up, even if we don't have any.
    firstAppRun.app.appComponent.relationshipsService.syncAndVerifyRelationships(account)

    firstAppRun.app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    firstAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.secondaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }

      awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    val secondAppRun = firstAppRun.relaunchApp()

    secondAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  test("Key rotation fails with a cleared hardware") {
    val firstAppRun = launchNewApp()
    firstAppRun.onboardFullAccountWithFakeHardware()

    firstAppRun.app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    firstAppRun.fakeNfcCommands.clearHardwareKeysAndFingerprintEnrollment()

    firstAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.secondaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }

      // Retry
      awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_ACCEPTABLE) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }

      // Dismiss
      awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.FAILED_TO_ROTATE_AUTH_ACCEPTABLE) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.secondaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * In this test we know the `eventTrackerScreenIdContext` would not be `SETTINGS`,
   * if the `DECIDE_IF_SHOULD_ROTATE_AUTH`, `ROTATING_AUTH`, or `SUCCESSFULLY_ROTATED_AUTH` screens
   * were shown as "overlay".
   */
  test("Rotating from settings doesn't trigger overlay for MoneyHome") {
    val firstAppRun = launchNewApp()
    firstAppRun.onboardFullAccountWithFakeHardware()

    firstAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel> {
        trailingToolbarAccessoryModel.shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick.invoke()
      }

      awaitUntilScreenWithBody<SettingsBodyModel> {
        val mobileDevicesRow = sectionModels.firstNotNullOfOrNull { section ->
          section.rowModels.firstOrNull {
            it.title.equals("Mobile devices", ignoreCase = true)
          }
        }

        mobileDevicesRow.shouldNotBeNull()
          .onClick.invoke()
      }

      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }

      awaitUntilScreenWithBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilScreenWithBody<SettingsBodyModel> {
        onBack()
      }

      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    val secondAppRun = firstAppRun.relaunchApp()

    secondAppRun.app.appUiStateMachine.test(Unit, useVirtualTime = false) {
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }
})

suspend fun ReceiveTurbine<ScreenModel>.screenDecideIfShouldRotate(
  validate: FormBodyModel.() -> Unit,
) {
  awaitUntilScreenWithBody<FormBodyModel>(
    id = InactiveAppEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH,
    // There are two "Rotate if should rotate" screens, one with two buttons and one with one button,
    // both have initial loading state. We need to wait for the loading state to clear.
    expectedBodyContentMatch = {
      it.primaryButton?.isEnabled ?: true && it.secondaryButton?.isEnabled ?: true
    },
    block = validate
  )
}
