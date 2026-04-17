package build.wallet.integration.statemachine.recovery.cloud

import app.cash.turbine.ReceiveTurbine
import bitkey.account.HardwareType
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.cloud.backup.CloudBackupStore
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.feature.setFlagValue
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.analytics.events.screen.id.NfcEventTrackerScreenId
import build.wallet.nfc.FakeFirmwareDeviceInfo
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.nfc.FakeW3FirmwareDeviceInfo
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.recovery.cloud.CloudBackupFailure
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.cloud.ProblemWithCloudBackupModel
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens
import build.wallet.statemachine.recovery.cloud.SelectCloudBackupBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.AppMode
import build.wallet.testing.ext.HardwareCoverageMode
import build.wallet.testing.ext.assertActiveHardwareType
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testForHardwareHappyPaths
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.seconds

class MultipleFullAccountCloudBackupsRestoreFunctionalTests : FunSpec({
  testForHardwareHappyPaths(
    "cloud restore works with shared cloud backups enabled when only one full backup exists"
  ) { appWithSingleBackup, coverageMode ->
    appWithSingleBackup.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    appWithSingleBackup.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val recoveringApp =
      launchAppInSameMode(
        templateApp = appWithSingleBackup,
        cloudStoreAccountRepository = appWithSingleBackup.cloudStoreAccountRepository,
        cloudBackupStore = appWithSingleBackup.cloudBackupStore,
        hardwareSeed = appWithSingleBackup.fakeHardwareKeyStore.getSeed(),
        w3HardwareSeed = appWithSingleBackup.w3FakeHardwareKeyStore.getSeed()
      )
    recoveringApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)

      // With only one full backup present, we should proceed directly to the restore prompt
      // (no selection screen).
      awaitUntilBody<CloudBackupFoundModel>()
        .onRestore()
      advanceThroughCloudRestoreUntilMoneyHome(coverageMode.hardwareType)

      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.assertActiveHardwareType(coverageMode.hardwareType)
  }

  testForHardwareHappyPaths(
    "cloud restore updates AppInstallation with hardware serial number"
  ) { originalApp, coverageMode ->
    originalApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    originalApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val recoveringApp =
      launchAppInSameMode(
        templateApp = originalApp,
        cloudStoreAccountRepository = originalApp.cloudStoreAccountRepository,
        cloudBackupStore = originalApp.cloudBackupStore,
        hardwareSeed = originalApp.fakeHardwareKeyStore.getSeed(),
        w3HardwareSeed = originalApp.w3FakeHardwareKeyStore.getSeed()
      )
    recoveringApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)

      awaitUntilBody<CloudBackupFoundModel>()
        .onRestore()
      advanceThroughCloudRestoreUntilMoneyHome(coverageMode.hardwareType)

      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.assertActiveHardwareType(coverageMode.hardwareType)

    // Verify hardware serial number was updated in AppInstallation to match the fake HW serial
    val expectedSerial = when (coverageMode.hardwareType) {
      HardwareType.W1 -> FakeFirmwareDeviceInfo.serial
      HardwareType.W3 -> FakeW3FirmwareDeviceInfo.serial
    }
    val appInstallation = recoveringApp.appInstallationDao.getOrCreateAppInstallation().getOrThrow()
    appInstallation.hardwareSerialNumber.shouldBe(expectedSerial)
  }

  testForHardwareHappyPaths(
    "cloud restore uses the first decryptable backup when multiple full account backups exist"
  ) { appWithDecryptableBackup, coverageMode ->
    appWithDecryptableBackup.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    val expectedRecoveredAccount =
      appWithDecryptableBackup.onboardFullAccountWithFakeHardware(
        cloudStoreAccountForBackup = CloudStoreAccount1Fake,
        hardwareType = coverageMode.hardwareType
      )

    val appWithNonDecryptableBackup =
      launchAppInSameMode(
        templateApp = appWithDecryptableBackup,
        cloudStoreAccountRepository = appWithDecryptableBackup.cloudStoreAccountRepository,
        cloudBackupStore = appWithDecryptableBackup.cloudBackupStore
    )
    appWithNonDecryptableBackup.sharedCloudBackupsFeatureFlag.setFlagValue(true)
    appWithNonDecryptableBackup.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val recoveringApp =
      launchAppInSameMode(
        templateApp = appWithDecryptableBackup,
        cloudStoreAccountRepository = appWithDecryptableBackup.cloudStoreAccountRepository,
        cloudBackupStore = appWithDecryptableBackup.cloudBackupStore,
        hardwareSeed = appWithDecryptableBackup.fakeHardwareKeyStore.getSeed(),
        w3HardwareSeed = appWithDecryptableBackup.w3FakeHardwareKeyStore.getSeed()
      )
    recoveringApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudBackupFoundModel>()
        .onRestore()

      advanceThroughCloudRestoreUntilMoneyHome(coverageMode.hardwareType)
      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.assertActiveHardwareType(coverageMode.hardwareType)
    recoveringApp.getActiveFullAccount().accountId.serverId
      .shouldBe(expectedRecoveredAccount.accountId.serverId)
  }

  testForHardwareHappyPaths(
    "cloud restore shows a decrypt failure when multiple full account backups exist but none decrypt"
  ) { appWithBackup1, coverageMode ->
    appWithBackup1.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    appWithBackup1.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val appWithBackup2 =
      launchAppInSameMode(
        templateApp = appWithBackup1,
        cloudStoreAccountRepository = appWithBackup1.cloudStoreAccountRepository,
        cloudBackupStore = appWithBackup1.cloudBackupStore
    )
    appWithBackup2.sharedCloudBackupsFeatureFlag.setFlagValue(true)
    appWithBackup2.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val unrelatedHardwareSeed = launchAppInSameMode(templateApp = appWithBackup1)
      .fakeHardwareKeyStore
      .getSeed()

    val recoveringApp =
      launchAppInSameMode(
        templateApp = appWithBackup1,
        cloudStoreAccountRepository = appWithBackup1.cloudStoreAccountRepository,
        cloudBackupStore = appWithBackup1.cloudBackupStore,
        hardwareSeed = unrelatedHardwareSeed
      )
    recoveringApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitUntilBody<CloudBackupFoundModel>()
        .onRestore()

      advanceThroughCloudRestoreUntilFailure(coverageMode.hardwareType)
        .failure.shouldBe(CloudBackupFailure.HWCantDecryptCSEK)

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForHardwareHappyPaths(
    "backup selector shows when cloud has both lite and full backups and selecting full proceeds to full restore"
  ) { fullAccountApp, coverageMode ->
    fullAccountApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    val fullAccount = fullAccountApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake,
      hardwareType = coverageMode.hardwareType
    )

    val liteAccountApp =
      launchAppInSameMode(
        templateApp = fullAccountApp,
        cloudStoreAccountRepository = fullAccountApp.cloudStoreAccountRepository,
        cloudBackupStore = fullAccountApp.cloudBackupStore
      )
    liteAccountApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    val liteAccount = liteAccountApp.createLiteAccountService.createAccount(
      liteAccountApp.accountConfigService.defaultConfig().value.toLiteAccountConfig()
    ).getOrThrow()
    liteAccountApp.accountService.setActiveAccount(liteAccount).getOrThrow()

    val liteBackup = liteAccountApp.liteAccountCloudBackupCreator.create(liteAccount).getOrThrow()
    liteAccountApp.cloudBackupService.writeBackup(
      accountId = liteAccount.accountId,
      cloudStoreAccount = CloudStoreAccount1Fake,
      backup = liteBackup,
      requireAuthRefresh = false
    ).getOrThrow()

    val recoveringApp =
      launchAppInSameMode(
        templateApp = fullAccountApp,
        cloudStoreAccountRepository = fullAccountApp.cloudStoreAccountRepository,
        cloudBackupStore = fullAccountApp.cloudBackupStore,
        hardwareSeed = fullAccountApp.fakeHardwareKeyStore.getSeed(),
        w3HardwareSeed = fullAccountApp.w3FakeHardwareKeyStore.getSeed()
      )
    recoveringApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    recoveringApp.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 20.seconds
    ) {
      awaitUntilBody<ChooseAccountAccessModel>()
        .clickMoreOptionsButton()
      awaitUntilBody<FormBodyModel>()
        .restoreButton.onClick.shouldNotBeNull().invoke()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)

      val selector = awaitUntilBody<SelectCloudBackupBodyModel>(
        CloudEventTrackerScreenId.SELECT_ACCOUNT_BACKUP
      )

      selector.backupItems.any { it.backup.accountId == liteAccount.accountId.serverId }
        .shouldBe(true)
      selector.backupItems.any { it.backup.accountId == fullAccount.accountId.serverId }
        .shouldBe(true)

      val fullBackupItem =
        selector.backupItems.first { it.backup.accountId == fullAccount.accountId.serverId }
      fullBackupItem.displayLabel.shouldContain("Wallet")
      selector.onBackupSelected(fullBackupItem.backup)

      awaitUntilBody<CloudBackupFoundModel>()
        .onRestore()
      advanceThroughCloudRestoreUntilMoneyHome(coverageMode.hardwareType)

      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.assertActiveHardwareType(coverageMode.hardwareType)
    recoveringApp.getActiveFullAccount().accountId.serverId
      .shouldBe(fullAccount.accountId.serverId)
  }
})

private suspend fun TestScope.launchAppInSameMode(
  templateApp: AppTester,
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudBackupStore: CloudBackupStore? = null,
  hardwareSeed: FakeHardwareKeyStore.Seed? = null,
  w3HardwareSeed: FakeHardwareKeyStore.Seed? = null,
): AppTester {
  val app =
    when (templateApp.appMode) {
    AppMode.Private ->
      launchNewApp(
        cloudStoreAccountRepository = cloudStoreAccountRepository,
        cloudBackupStore = cloudBackupStore,
        hardwareSeed = hardwareSeed,
        w3HardwareSeed = w3HardwareSeed
      )

    AppMode.Legacy ->
      launchLegacyWalletApp(
        cloudStoreAccountRepository = cloudStoreAccountRepository,
        cloudBackupStore = cloudBackupStore,
        hardwareSeed = hardwareSeed,
        w3HardwareSeed = w3HardwareSeed
      )
    }

  templateApp.accountConfigService.defaultConfig().value.hardwareType?.let {
    app.accountConfigService.setHardwareType(it).getOrThrow()
  }

  return app
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCloudRestoreUntilMoneyHome(
  hardwareType: HardwareType = HardwareType.W1,
) {
  if (hardwareType == HardwareType.W3) {
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
  }
  // Auth rotation decision - wait until the button is enabled (keys have been generated)
  awaitUntilBody<RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice>(
    matching = { it.removeAllOtherDevicesEnabled }
  ) {
    onRemoveAllOtherDevices()
  }
  // For W3, auth rotation triggers a confirmable NFC session
  if (hardwareType == HardwareType.W3) {
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
  }
  awaitUntilBody<RotateAuthKeyScreens.Confirmation> { onSelected() }
  awaitUntilBody<MoneyHomeBodyModel>()
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCloudRestoreUntilFailure(
  hardwareType: HardwareType = HardwareType.W1,
): ProblemWithCloudBackupModel {
  if (hardwareType == HardwareType.W3) {
    awaitUntilScreenWithBody<BodyModel>(
      matchingScreen = { it.bottomSheetModel?.body is PromptSelectionFormBodyModel }
    ).let { (checkNotNull(it.bottomSheetModel).body as PromptSelectionFormBodyModel).onApprove() }
    awaitUntilBody<HardwareConfirmationScreenModel> { onConfirm() }
    // NFC fails because hardware keys don't match — error is handled by onError,
    // which transitions directly to ProblemWithCloudBackupModel
  }
  return awaitUntilBody<ProblemWithCloudBackupModel>()
}
