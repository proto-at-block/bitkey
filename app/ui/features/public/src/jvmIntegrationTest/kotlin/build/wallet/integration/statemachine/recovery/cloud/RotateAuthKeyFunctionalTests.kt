package build.wallet.integration.statemachine.recovery.cloud

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.plusAssign
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.context.AuthKeyRotationEventTrackerScreenIdContext
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.bitkey.app.AppGlobalAuthKey
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.coroutines.turbine.turbines
import build.wallet.crypto.PublicKey
import build.wallet.relationships.syncAndVerifyRelationships
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIOrigin
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyUIStateMachineProps
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.settings.SettingsBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.testing.ext.assertActiveHardwareType
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testForHardwareHappyPaths
import build.wallet.ui.model.toolbar.ToolbarAccessoryModel
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.types.shouldBeTypeOf

class RotateAuthKeyFunctionalTests : FunSpec({
  suspend fun setupAccountForRotation(
    app: build.wallet.testing.AppTester,
    coverageMode: HardwareCoverageMode,
  ) {
    val account = app.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    // Auth key rotation depends on cloud backup upload, which requires SocRec relationships to be
    // synced up, even if we don't have any.
    app.relationshipsService.syncAndVerifyRelationships(account)
  }

  suspend fun ReceiveTurbine<ScreenModel>.completeRotationHardwareAuth(
    coverageMode: HardwareCoverageMode,
  ) {
    if (coverageMode == HardwareCoverageMode.W3Private) {
      awaitUntilScreenWithBody<BodyModel>(
        matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
      ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
      awaitUntilBody<HardwareConfirmationScreenModel> {
        onConfirm()
      }
    }
  }

  testForHardwareHappyPaths("Proposed rotation flag is persisted") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)

    app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    app.appUiStateMachine.test(Unit) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }
    }

    val secondAppRun = app.relaunchApp()

    secondAppRun.appUiStateMachine.test(Unit) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }
    }
  }

  testForHardwareHappyPaths("User can clear proposed rotation flag") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)

    app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    app.appUiStateMachine.test(Unit) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION

        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    val secondAppRun = app.relaunchApp()

    secondAppRun.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForHardwareHappyPaths("User can successfully rotate keys from proposal") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)

    app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    app.appUiStateMachine.test(Unit) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.secondaryButton.shouldNotBeNull().onClick.invoke()
      }
      completeRotationHardwareAuth(coverageMode)

      awaitUntilBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }

      awaitUntilBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
  }

  testForHardwareHappyPaths("User can successfully rotate keys from settings") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)
    val account = app.getActiveFullAccount()

    val onBackCalls = turbines.create<Unit>("onBackCalls-${coverageMode.name}")

    val props = RotateAuthKeyUIStateMachineProps(
      account = account,
      origin = RotateAuthKeyUIOrigin.Settings(
        onBack = {
          onBackCalls += Unit
        }
      )
    )
    app.rotateAuthUIStateMachine.test(props) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }
      completeRotationHardwareAuth(coverageMode)

      awaitUntilBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }

      awaitUntilBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      onBackCalls.awaitItem()
    }

    app.assertActiveHardwareType(coverageMode.hardwareType)
  }

  testForHardwareHappyPaths("User can successfully rotate keys twice from settings in one session") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)

    val initialAuthKey = app.getActiveFullAccount().keybox.activeAppKeyBundle.authKey

    app.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel> {
        trailingToolbarAccessoryModel.shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick.invoke()
      }

      awaitUntilBody<SettingsBodyModel> {
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
      completeRotationHardwareAuth(coverageMode)

      awaitUntilBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }

      awaitUntilBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      var firstRotatedAuthKey: PublicKey<AppGlobalAuthKey>? = null
      awaitUntilBody<SettingsBodyModel> {
        firstRotatedAuthKey = app.getActiveFullAccount().keybox.activeAppKeyBundle.authKey
        firstRotatedAuthKey.shouldNotBe(initialAuthKey)

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
      completeRotationHardwareAuth(coverageMode)

      awaitUntilBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }

      awaitUntilBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilBody<SettingsBodyModel> {
        onBack()
      }

      val secondRotatedAuthKey = app.getActiveFullAccount().keybox.activeAppKeyBundle.authKey
      secondRotatedAuthKey.shouldNotBe(firstRotatedAuthKey)
      secondRotatedAuthKey.shouldNotBe(initialAuthKey)

      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  testForHardwareHappyPaths("Key rotation resumes from previous attempt") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)

    app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    app.appUiStateMachine.test(Unit) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.secondaryButton.shouldNotBeNull().onClick.invoke()
      }
      completeRotationHardwareAuth(coverageMode)

      awaitUntilBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
      }

      awaitUntilBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    val secondAppRun = app.relaunchApp()

    secondAppRun.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForHardwareHappyPaths("Key rotation fails with a cleared hardware") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)

    app.fullAccountAuthKeyRotationService.recommendKeyRotation()

    // Simulate a wiped device
    when (coverageMode.hardwareType) {
      HardwareType.W1 -> app.fakeNfcCommands.wipeDevice()
      HardwareType.W3 -> app.fakeW3NfcCommands.wipeDevice()
    }

    app.appUiStateMachine.test(Unit) {
      screenDecideIfShouldRotate {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
        this.secondaryButton.shouldNotBeNull().onClick.invoke()
      }

      when (coverageMode) {
        HardwareCoverageMode.W1Baseline -> {
          // W1: NFC fails immediately (can't sign with wiped keys)
          awaitUntilBody<FormBodyModel>(NfcEventTrackerScreenId.NFC_FAILURE) {
            this.primaryButton.shouldNotBeNull().onClick.invoke()
          }

          // After NFC failure, user returns to the decision screen
          screenDecideIfShouldRotate {
            eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
            this.primaryButton.shouldNotBeNull().onClick.invoke()
          }
        }
        HardwareCoverageMode.W3Private -> {
          // W3: NFC two-tap completes (signing doesn't fail at NFC layer),
          // but rotation fails server-side with an acceptable failure
          completeRotationHardwareAuth(coverageMode)

          awaitUntilBody<RotateAuthKeyScreens.AcceptableFailure> {
            eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.PROPOSED_ROTATION
            onAcknowledge()
          }
        }
      }

      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }
  }

  /**
   * In this test we know the `eventTrackerScreenIdContext` would not be `SETTINGS`,
   * if the `DECIDE_IF_SHOULD_ROTATE_AUTH`, `ROTATING_AUTH`, or `SUCCESSFULLY_ROTATED_AUTH` screens
   * were shown as "overlay".
   */
  testForHardwareHappyPaths("Rotating from settings doesn't trigger overlay for MoneyHome") { app, coverageMode ->
    setupAccountForRotation(app, coverageMode)

    app.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel> {
        trailingToolbarAccessoryModel.shouldBeTypeOf<ToolbarAccessoryModel.IconAccessory>()
          .model.onClick.invoke()
      }

      awaitUntilBody<SettingsBodyModel> {
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
      completeRotationHardwareAuth(coverageMode)

      awaitUntilBody<LoadingSuccessBodyModel>(InactiveAppEventTrackerScreenId.ROTATING_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
      }

      awaitUntilBody<FormBodyModel>(InactiveAppEventTrackerScreenId.SUCCESSFULLY_ROTATED_AUTH) {
        eventTrackerContext shouldBe AuthKeyRotationEventTrackerScreenIdContext.SETTINGS
        this.primaryButton.shouldNotBeNull().onClick.invoke()
      }

      awaitUntilBody<SettingsBodyModel> {
        onBack()
      }

      awaitUntilBody<MoneyHomeBodyModel>()
      cancelAndIgnoreRemainingEvents()
    }

    val secondAppRun = app.relaunchApp()

    secondAppRun.appUiStateMachine.test(Unit) {
      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }
  }
})

suspend fun ReceiveTurbine<ScreenModel>.screenDecideIfShouldRotate(
  validate: FormBodyModel.() -> Unit,
) {
  awaitUntilBody<FormBodyModel>(
    id = InactiveAppEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH,
    matching = {
      it.primaryButton?.isEnabled ?: true && it.secondaryButton?.isEnabled ?: true
    },
    validate = validate
  )
}

suspend fun ReceiveTurbine<ScreenModel>.openRotateAuthFromSettings() {
  awaitUntilBody<SettingsBodyModel> {
    val mobileDevicesRow = sectionModels.firstNotNullOfOrNull { section ->
      section.rowModels.firstOrNull {
        it.title.equals("Mobile devices", ignoreCase = true)
      }
    }

    mobileDevicesRow.shouldNotBeNull()
      .onClick.invoke()
  }
}
