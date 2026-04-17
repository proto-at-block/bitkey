package build.wallet.integration.statemachine.recovery.emergencyexitkit

import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.LOADING_BACKUP
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.emergencyexitkit.EmergencyExitKitBackup
import build.wallet.emergencyexitkit.EmergencyExitKitPayload.EmergencyExitKitPayloadV1
import build.wallet.emergencyexitkit.EmergencyExitKitPayloadDecoderImpl
import build.wallet.encrypt.SymmetricKeyEncryptorImpl
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.money.BitcoinMoney
import build.wallet.nfc.platform.sealSymmetricKey
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitImportPasteAppKeyBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitImportWalletBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRestoreWalletBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.ext.AppMode
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.testing.ext.assertActiveHardwareType
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.shouldHaveTotalBalance
import build.wallet.testing.ext.testForHardwareHappyPaths
import build.wallet.testing.fakeTransact
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.test.TestScope
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import app.cash.turbine.ReceiveTurbine
import kotlin.time.Duration.Companion.seconds

class EmergencyExitRecoveryFunctionalTests : FunSpec({
  testForHardwareHappyPaths("recover keybox with no funds from Emergency Exit Kit", isFlakyTest = true) { app, coverageMode ->
    // Onboard a new account, and generate an EEK payload.
    app.onboardFullAccountWithFakeHardware(hardwareType = coverageMode.hardwareType)

    val csek = app.sekGenerator.generate()

    val sealedCsek =
      app.nfcTransactor.fakeTransact(
        hardwareType = coverageMode.hardwareType,
        transaction = { session, commands ->
          commands.sealSymmetricKey(session, csek.key)
        }
      ).getOrThrow()

    val spendingKeys = app.getActiveFullAccount().keybox.activeSpendingKeyset
    val xprv = app.appPrivateKeyDao.getAppSpendingPrivateKey(spendingKeys.appKey)
      .get().shouldNotBeNull()

    // TODO (BKR-923): There is no PDF creation implementation for the JVM, preventing the real
    //      creation of an Emergency Exit Kit PDF. This simulates the same creation so that
    //      the account that restores from it can validate it's the same spending keys.
    val sealedSpendingKeys = SymmetricKeyEncryptorImpl().sealNoMetadata(
      unsealedData = EmergencyExitKitPayloadDecoderImpl().encodeBackup(
        EmergencyExitKitBackup.EmergencyExitKitBackupV1(
          spendingKeyset = spendingKeys,
          appSpendingKeyXprv = xprv
        )
      ),
      key = csek.key
    )
    val validData =
      EmergencyExitKitPayloadDecoderImpl().encode(
        EmergencyExitKitPayloadV1(
          sealedHwEncryptionKey = sealedCsek,
          sealedActiveSpendingKeys = sealedSpendingKeys
        )
      )

    // New app, same hardware, no cloud backup.
    val newApp = launchAppMatchingMode(
      referenceApp = app,
      hardwareSeed = app.fakeHardwareKeyStore.getSeed(),
      w3HardwareSeed = app.w3FakeHardwareKeyStore.getSeed()
    )
    newApp.accountConfigService.setHardwareType(coverageMode.hardwareType).getOrThrow()

    newApp.appUiStateMachine.test(
      Unit,
      turbineTimeout = 10.seconds
    ) {
      // Do not find backup, enter the EEK flow.
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<FormBodyModel>(CLOUD_BACKUP_NOT_FOUND)
        .restoreEmergencyExitButton.onClick.shouldNotBeNull().invoke()

      // Progress through the EEK flow with manual entry.
      awaitUntilBody<EmergencyExitKitImportWalletBodyModel>()
        .onEnterManually()
      awaitUntilBody<EmergencyExitKitImportPasteAppKeyBodyModel> {
        onEnterTextChanged(validData)
      }
      awaitUntilBody<EmergencyExitKitImportPasteAppKeyBodyModel>(
        matching = { it.primaryButton?.isEnabled == true }
      ) {
        enteredText.shouldBe(validData)
        onContinue()
      }
      awaitUntilBody<EmergencyExitKitRestoreWalletBodyModel>(
        matching = { it.primaryButton?.isEnabled == true }
      ) {
        onRestore.shouldNotBeNull().invoke()
      }

      advanceThroughEmergencyExitRestoreUntilMoneyHome(coverageMode.hardwareType)

      // Validate that this is the same wallet as originally created.
      newApp.shouldHaveTotalBalance(BitcoinMoney.zero())

      newApp.getActiveFullAccount().keybox.activeSpendingKeyset.appKey
        .shouldBeEqual(spendingKeys.appKey)

      cancelAndIgnoreRemainingEvents()
    }

    newApp.assertActiveHardwareType(coverageMode.hardwareType)
  }

  test("user text is redacted") {
    val model = EmergencyExitKitImportPasteAppKeyBodyModel(
      enteredText = "test",
      onBack = {},
      onEnterTextChanged = {},
      onPasteButtonClick = {},
      onContinue = {}
    )

    model.enteredText.shouldContain("test")
    model.toString().shouldNotContain("test")
  }
})

private suspend fun TestScope.launchAppMatchingMode(
  referenceApp: AppTester,
  hardwareSeed: build.wallet.nfc.FakeHardwareKeyStore.Seed? = null,
  w3HardwareSeed: build.wallet.nfc.FakeHardwareKeyStore.Seed? = null,
): AppTester =
  if (referenceApp.appMode == AppMode.Private) {
    launchNewApp(hardwareSeed = hardwareSeed, w3HardwareSeed = w3HardwareSeed)
  } else {
    launchLegacyWalletApp(hardwareSeed = hardwareSeed, w3HardwareSeed = w3HardwareSeed)
  }

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughEmergencyExitRestoreUntilMoneyHome(
  hardwareType: HardwareType = HardwareType.W1,
) {
  if (hardwareType == HardwareType.W3) {
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
  }
  awaitUntilBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
    state.shouldBe(LoadingSuccessBodyModel.State.Loading)
  }
  awaitUntilBody<MoneyHomeBodyModel>()
}

private val FormBodyModel.restoreEmergencyExitButton: ListItemModel
  get() =
    mainContentList.first()
      .shouldBeTypeOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .items[2]
