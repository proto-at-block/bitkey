package build.wallet.integration.statemachine.recovery.emergencyaccesskit

import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_BACKUP_NOT_FOUND
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId.CLOUD_SIGN_IN_LOADING
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.IMPORT_TEXT_KEY
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.LOADING_BACKUP
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.RESTORE_YOUR_WALLET
import build.wallet.analytics.events.screen.id.EmergencyAccessKitTrackerScreenId.SELECT_IMPORT_METHOD
import build.wallet.cloud.backup.csek.Csek
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.crypto.SymmetricKeyImpl
import build.wallet.emergencyaccesskit.EmergencyAccessKitBackup
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayload.EmergencyAccessKitPayloadV1
import build.wallet.emergencyaccesskit.EmergencyAccessKitPayloadDecoderImpl
import build.wallet.encrypt.SymmetricKeyEncryptorImpl
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.form.FormMainContentModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.launchNewApp
import build.wallet.ui.model.list.ListItemModel
import com.github.michaelbull.result.get
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import okio.ByteString.Companion.decodeHex
import kotlin.time.Duration.Companion.seconds

class EmergencyAccessRecoveryFunctionalTests : FunSpec({
  lateinit var app: AppTester

  beforeTest {
    app = launchNewApp()
  }

  test("recover keybox with no funds from emergency access kit") {
    // Onboard a new account, and generate an EAK payload.
    app.onboardFullAccountWithFakeHardware()

    // TODO (BKR-923): There is no PDF creation implementation for the JVM, preventing the real
    // creation of an emergency access kit PDF. This simulates the same creation so that
    // the account that restores from it can validate it's the same spending keys.
    val csekFake =
      Csek(key = SymmetricKeyImpl(raw = "b8ef0c208d341bf262638a7ecf142beab8ef0c208d341bf262638a7ecf142bea".decodeHex()))
    val spendingKeys = app.getActiveFullAccount().keybox.activeSpendingKeyset
    val xprv = app.app.appComponent.appPrivateKeyDao.getAppSpendingPrivateKey(spendingKeys.appKey)
      .get().shouldNotBeNull()
    val validData =
      EmergencyAccessKitPayloadDecoderImpl.encode(
        EmergencyAccessKitPayloadV1(
          sealedHwEncryptionKey = csekFake.key.raw,
          sealedActiveSpendingKeys = SymmetricKeyEncryptorImpl().seal(
            unsealedData = EmergencyAccessKitPayloadDecoderImpl.encodeBackup(
              EmergencyAccessKitBackup.EmergencyAccessKitBackupV1(
                spendingKeyset = spendingKeys,
                appSpendingKeyXprv = xprv
              )
            ),
            key = csekFake.key
          )
        )
      )

    // Don't set a cloud store, so the backup isn't "found".
    val newApp = launchNewApp()

    newApp.app.appUiStateMachine.test(
      Unit,
      useVirtualTime = false,
      turbineTimeout = 10.seconds
    ) {
      // Do not find backup, enter the EAK flow.
      awaitUntilScreenWithBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilScreenWithBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilScreenWithBody<CloudSignInModelFake>(CLOUD_SIGN_IN_LOADING)
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilScreenWithBody<FormBodyModel>(CLOUD_BACKUP_NOT_FOUND)
        .restoreEmergencyAccessButton.onClick.shouldNotBeNull().invoke()

      // Progress through the EAK flow with manual entry.
      awaitUntilScreenWithBody<FormBodyModel>(SELECT_IMPORT_METHOD)
        .clickPrimaryButton()
      awaitUntilScreenWithBody<FormBodyModel>(IMPORT_TEXT_KEY) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .onValueChange(validData, IntRange(0, validData.length))
      }
      awaitUntilScreenWithBody<FormBodyModel>(
        IMPORT_TEXT_KEY,
        expectedBodyContentMatch = { it.primaryButton?.isEnabled == true }
      ) {
        this.mainContentList.first()
          .shouldBeTypeOf<FormMainContentModel.AddressInput>()
          .fieldModel
          .value
          .shouldBe(validData)

        clickPrimaryButton()
      }
      awaitUntilScreenWithBody<FormBodyModel>(RESTORE_YOUR_WALLET)
        .clickPrimaryButton()

      awaitUntilScreenWithBody<NfcBodyModel>()
      awaitUntilScreenWithBody<NfcBodyModel>()

      awaitUntilScreenWithBody<LoadingSuccessBodyModel>(LOADING_BACKUP) {
        state.shouldBe(LoadingSuccessBodyModel.State.Loading)
      }

      // Validate that this is the same wallet as originally created.
      awaitUntilScreenWithBody<MoneyHomeBodyModel>()
        .balanceModel.secondaryAmount.shouldBe("0 sats")

      newApp.getActiveFullAccount().keybox.activeSpendingKeyset.appKey
        .shouldBeEqual(spendingKeys.appKey)

      cancelAndIgnoreRemainingEvents()
    }
  }
})

private val FormBodyModel.restoreEmergencyAccessButton: ListItemModel
  get() =
    mainContentList.first()
      .shouldBeTypeOf<FormMainContentModel.ListGroup>()
      .listGroupModel
      .items[2]
