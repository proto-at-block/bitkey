package build.wallet.integration.statemachine.recovery.emergencyexitkit

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
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitImportPasteAppKeyBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitImportWalletBodyModel
import build.wallet.statemachine.recovery.emergencyexitkit.EmergencyExitKitRestoreWalletBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.shouldHaveTotalBalance
import build.wallet.testing.ext.testForLegacyAndPrivateWallet
import build.wallet.testing.fakeTransact
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.get
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class EmergencyExitRecoveryFunctionalTests : FunSpec({
  testForLegacyAndPrivateWallet("recover keybox with no funds from Emergency Exit Kit", isFlakyTest = true) {
    val app = launchNewApp()
    // Onboard a new account, and generate an EEK payload.
    app.onboardFullAccountWithFakeHardware()

    val csek = app.sekGenerator.generate()

    val sealedCsek =
      app.nfcTransactor.fakeTransact(
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
    val newApp = launchNewApp(
      hardwareSeed = app.fakeHardwareKeyStore.getSeed()
    )

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

      awaitUntilBody<NfcBodyModel>()
      awaitUntilBody<NfcBodyModel>()

      awaitUntilBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Validate that this is the same wallet as originally created.
      awaitUntilBody<MoneyHomeBodyModel>()
      newApp.shouldHaveTotalBalance(BitcoinMoney.zero())

      newApp.getActiveFullAccount().keybox.activeSpendingKeyset.appKey
        .shouldBeEqual(spendingKeys.appKey)

      cancelAndIgnoreRemainingEvents()
    }
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

private val FormBodyModel.restoreEmergencyExitButton: ListItemModel
  get() =
    mainContentList.first()
      .shouldBeTypeOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .items[2]
