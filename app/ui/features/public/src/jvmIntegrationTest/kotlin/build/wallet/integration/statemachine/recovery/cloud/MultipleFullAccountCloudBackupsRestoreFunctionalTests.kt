package build.wallet.integration.statemachine.recovery.cloud

import app.cash.turbine.ReceiveTurbine
import build.wallet.analytics.events.screen.id.CloudEventTrackerScreenId
import build.wallet.analytics.events.screen.id.InactiveAppEventTrackerScreenId
import build.wallet.cloud.store.CloudKeyValueStore
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.cloud.store.CloudStoreAccountRepository
import build.wallet.coroutines.turbine.awaitUntil
import build.wallet.feature.setFlagValue
import build.wallet.integration.statemachine.create.restoreButton
import build.wallet.nfc.FakeHardwareKeyStore
import build.wallet.statemachine.account.ChooseAccountAccessModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.core.LoadingSuccessBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.NfcBodyModel
import build.wallet.statemachine.recovery.cloud.CloudBackupFailure
import build.wallet.statemachine.recovery.cloud.CloudBackupFoundModel
import build.wallet.statemachine.recovery.cloud.ProblemWithCloudBackupModel
import build.wallet.statemachine.recovery.cloud.RotateAuthKeyScreens
import build.wallet.statemachine.recovery.cloud.SelectCloudBackupBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickMoreOptionsButton
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.AppMode
import build.wallet.testing.ext.getActiveFullAccount
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.testForLegacyAndPrivateWallet
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import kotlin.time.Duration.Companion.seconds

class MultipleFullAccountCloudBackupsRestoreFunctionalTests : FunSpec({
  testForLegacyAndPrivateWallet(
    "cloud restore works with shared cloud backups enabled when only one full backup exists"
  ) { appWithSingleBackup ->
    appWithSingleBackup.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    appWithSingleBackup.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    val recoveringApp =
      launchAppInSameMode(
        templateApp = appWithSingleBackup,
        cloudStoreAccountRepository = appWithSingleBackup.cloudStoreAccountRepository,
        cloudKeyValueStore = appWithSingleBackup.cloudKeyValueStore,
        hardwareSeed = appWithSingleBackup.fakeHardwareKeyStore.getSeed()
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
      advanceThroughCloudRestoreUntilMoneyHome()

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForLegacyAndPrivateWallet(
    "cloud restore uses the first decryptable backup when multiple full account backups exist"
  ) { appWithDecryptableBackup ->
    appWithDecryptableBackup.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    val expectedRecoveredAccount =
      appWithDecryptableBackup.onboardFullAccountWithFakeHardware(
        cloudStoreAccountForBackup = CloudStoreAccount1Fake
      )

    val appWithNonDecryptableBackup =
      launchAppInSameMode(
        templateApp = appWithDecryptableBackup,
        cloudStoreAccountRepository = appWithDecryptableBackup.cloudStoreAccountRepository,
        cloudKeyValueStore = appWithDecryptableBackup.cloudKeyValueStore
      )
    appWithNonDecryptableBackup.sharedCloudBackupsFeatureFlag.setFlagValue(true)
    appWithNonDecryptableBackup.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    val recoveringApp =
      launchAppInSameMode(
        templateApp = appWithDecryptableBackup,
        cloudStoreAccountRepository = appWithDecryptableBackup.cloudStoreAccountRepository,
        cloudKeyValueStore = appWithDecryptableBackup.cloudKeyValueStore,
        hardwareSeed = appWithDecryptableBackup.fakeHardwareKeyStore.getSeed()
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

      advanceThroughCloudRestoreUntilMoneyHome()
      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.getActiveFullAccount().accountId.serverId
      .shouldBe(expectedRecoveredAccount.accountId.serverId)
  }

  testForLegacyAndPrivateWallet(
    "cloud restore shows a decrypt failure when multiple full account backups exist but none decrypt"
  ) { appWithBackup1 ->
    appWithBackup1.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    appWithBackup1.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    val appWithBackup2 =
      launchAppInSameMode(
        templateApp = appWithBackup1,
        cloudStoreAccountRepository = appWithBackup1.cloudStoreAccountRepository,
        cloudKeyValueStore = appWithBackup1.cloudKeyValueStore
      )
    appWithBackup2.sharedCloudBackupsFeatureFlag.setFlagValue(true)
    appWithBackup2.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    val unrelatedHardwareSeed = launchAppInSameMode(templateApp = appWithBackup1)
      .fakeHardwareKeyStore
      .getSeed()

    val recoveringApp =
      launchAppInSameMode(
        templateApp = appWithBackup1,
        cloudStoreAccountRepository = appWithBackup1.cloudStoreAccountRepository,
        cloudKeyValueStore = appWithBackup1.cloudKeyValueStore,
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

      awaitUntilBody<ProblemWithCloudBackupModel> {
        failure.shouldBe(CloudBackupFailure.HWCantDecryptCSEK)
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForLegacyAndPrivateWallet(
    "backup selector shows when cloud has both lite and full backups and selecting full proceeds to full restore"
  ) { fullAccountApp ->
    fullAccountApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    val fullAccount = fullAccountApp.onboardFullAccountWithFakeHardware(
      cloudStoreAccountForBackup = CloudStoreAccount1Fake
    )

    val liteAccountApp =
      launchAppInSameMode(
        templateApp = fullAccountApp,
        cloudStoreAccountRepository = fullAccountApp.cloudStoreAccountRepository,
        cloudKeyValueStore = fullAccountApp.cloudKeyValueStore
      )
    liteAccountApp.sharedCloudBackupsFeatureFlag.setFlagValue(true)

    val liteAccount = liteAccountApp.createLiteAccountService.createAccount(
      liteAccountApp.accountConfigService.defaultConfig().value.toLiteAccountConfig()
    ).getOrThrow()
    liteAccountApp.accountService.setActiveAccount(liteAccount).getOrThrow()

    val liteBackup = liteAccountApp.liteAccountCloudBackupCreator.create(liteAccount).getOrThrow()
    liteAccountApp.cloudBackupRepository.writeBackup(
      accountId = liteAccount.accountId,
      cloudStoreAccount = CloudStoreAccount1Fake,
      backup = liteBackup,
      requireAuthRefresh = false
    ).getOrThrow()

    val recoveringApp =
      launchAppInSameMode(
        templateApp = fullAccountApp,
        cloudStoreAccountRepository = fullAccountApp.cloudStoreAccountRepository,
        cloudKeyValueStore = fullAccountApp.cloudKeyValueStore,
        hardwareSeed = fullAccountApp.fakeHardwareKeyStore.getSeed()
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
      advanceThroughCloudRestoreUntilMoneyHome()

      cancelAndIgnoreRemainingEvents()
    }

    recoveringApp.getActiveFullAccount().accountId.serverId
      .shouldBe(fullAccount.accountId.serverId)
  }
})

private suspend fun TestScope.launchAppInSameMode(
  templateApp: AppTester,
  cloudStoreAccountRepository: CloudStoreAccountRepository? = null,
  cloudKeyValueStore: CloudKeyValueStore? = null,
  hardwareSeed: FakeHardwareKeyStore.Seed? = null,
): AppTester {
  return when (templateApp.appMode) {
    AppMode.Private ->
      launchNewApp(
        cloudStoreAccountRepository = cloudStoreAccountRepository,
        cloudKeyValueStore = cloudKeyValueStore,
        hardwareSeed = hardwareSeed
      )

    AppMode.Legacy ->
      launchLegacyWalletApp(
        cloudStoreAccountRepository = cloudStoreAccountRepository,
        cloudKeyValueStore = cloudKeyValueStore,
        hardwareSeed = hardwareSeed
      )
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCloudRestoreUntilMoneyHome() {
  awaitUntil { screenModel ->
    val bodyModel = screenModel.body

    // Handle intermediate screens
    when (bodyModel) {
      is RotateAuthKeyScreens.DeactivateDevicesAfterRestoreChoice -> {
        if (bodyModel.removeAllOtherDevicesEnabled) {
          bodyModel.onRemoveAllOtherDevices()
        }
        false // Keep waiting
      }
      is RotateAuthKeyScreens.Confirmation -> {
        bodyModel.onSelected()
        false // Keep waiting
      }
      is FormBodyModel -> {
        if (bodyModel.id == InactiveAppEventTrackerScreenId.DECIDE_IF_SHOULD_ROTATE_AUTH) {
          bodyModel.clickPrimaryButton()
        }
        false // Keep waiting
      }
      is NfcBodyModel, is LoadingSuccessBodyModel -> false // Wait for NFC/success to dismiss
      is MoneyHomeBodyModel -> true // Done!
      else -> error("Unexpected screen model: $bodyModel")
    }
  }
}
