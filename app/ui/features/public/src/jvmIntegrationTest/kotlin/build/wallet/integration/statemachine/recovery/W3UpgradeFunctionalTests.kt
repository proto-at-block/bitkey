package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.store.CloudStoreAccount
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.feature.setFlagValue
import build.wallet.integration.statemachine.send.clickApprove
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.form.FormBodyModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.TransferConfirmationScreenModel
import build.wallet.statemachine.send.TransferInitiatedBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.statemachine.walletmigration.*
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.decryptCloudBackupKeys
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
import build.wallet.testing.ext.verifyCanUseKeyboxKeysets
import build.wallet.testing.ext.waitForFunds
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlin.time.Duration.Companion.seconds

class W3UpgradeFunctionalTests : FunSpec({

  test("W3 upgrade happy path - zero balance") {
    val app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)


    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      advanceThroughCloudBackup()

      // Zero balance - sweep is skipped entirely
      dismissW3UpgradeCompleteSheet()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }

  test("W3 upgrade repairs legacy canUseKeyboxKeysets state") {
    val app = launchLegacyWalletApp()
    app.onboardingCanUseKeyboxKeysetsFeatureFlag.setFlagValue(false)
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)
    app.verifyCanUseKeyboxKeysets(false)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      advanceThroughCloudBackup()

      dismissW3UpgradeCompleteSheet()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
    app.decryptCloudBackupKeys(CloudStoreAccount1Fake).keysets.size.shouldBe(2)
  }

  test("W3 upgrade happy path - sweep real funds") {
    val app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)
    app.addSomeFunds(sats(10_000L))


    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      val backupResult = advanceThroughCloudBackup()

      // Has funds - sweep flow
      val oldHardwareInstructions = (backupResult.body as? W3UpgradeOldHardwareInstructionsBodyModel)
        ?: awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel>()
      oldHardwareInstructions.onContinue()
      awaitUntilBody<TransferConfirmationScreenModel>()
        .clickPrimaryButton()
      awaitUntilBody<TransferInitiatedBodyModel>()
        .clickPrimaryButton()

      dismissW3UpgradeCompleteSheet()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
    app.waitForFunds()
    app.returnFundsToTreasury()
  }

  test("W3 upgrade - force exit during pre-keyset auth rotation returns to Money Home") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)


    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()

      // Wait for auth rotation instructions to appear, then force exit
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    // Relaunch before the point of no return: keyset creation has not happened yet,
    // so there is no persisted migration to auto-resume.
    app = app.relaunchApp()


    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      awaitUntilBody<MoneyHomeBodyModel>()

      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()

      advanceThroughCloudBackup()

      // Zero balance - sweep skipped
      dismissW3UpgradeCompleteSheet()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }

  test("W3 upgrade - force exit during cloud backup resumes (zero balance)") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)


    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      advanceThroughCloudBackup()

      // Force exit immediately after triggering cloud backup sign-in.
      // The backup may or may not have persisted yet.
      cancelAndIgnoreRemainingEvents()
    }

    // Relaunch
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Force-exit after triggering cloud backup is racy for zero balance:
      // backup may or may not have persisted, and on resume the upgrade may have
      // fully completed and cleaned up the sheet entirely. Handle every path.
      val screen = awaitUntilScreenWithBody<BodyModel>(
        matchingScreen = {
          it.body is CloudSignInModelFake ||
            (it.body as? SaveBackupInstructionsBodyModel)?.isLoading == false ||
            it.bottomSheetModel?.body is W3UpgradeCompleteSheetBodyModel ||
            it.body is MoneyHomeBodyModel
        }
      )
      when {
        screen.body is SaveBackupInstructionsBodyModel -> {
          (screen.body as SaveBackupInstructionsBodyModel).onBackupClick()
          dismissW3UpgradeCompleteSheet()
        }
        screen.body is CloudSignInModelFake -> {
          (screen.body as CloudSignInModelFake).signInSuccess(CloudStoreAccount1Fake)
          dismissW3UpgradeCompleteSheet()
        }
        screen.bottomSheetModel?.body is W3UpgradeCompleteSheetBodyModel -> {
          // Backup already completed before exit; resume landed past cloud backup
          dismissW3UpgradeCompleteSheet()
        }
        // Plain MoneyHome means the upgrade fully completed and cleaned up.
      }

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }

  test("W3 upgrade - force exit after cloud backup resumes at sweep with funds") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)
    app.addSomeFunds(sats(10_000L))


    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      val backupResult = advanceThroughCloudBackup()

      // Wait for the sweep instructions to confirm backup completed, then force exit
      if (backupResult.body !is W3UpgradeOldHardwareInstructionsBodyModel) {
        awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel>()
      }

      cancelAndIgnoreRemainingEvents()
    }

    // Relaunch — cloud backup already completed, resumes at CheckingForFunds
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Has funds -> sweep
      awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel>()
        .onContinue()
      awaitUntilBody<TransferConfirmationScreenModel>()
        .clickPrimaryButton()
      awaitUntilBody<TransferInitiatedBodyModel>()
        .clickPrimaryButton()

      dismissW3UpgradeCompleteSheet()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
    app.waitForFunds()
    app.returnFundsToTreasury()
  }

  test("W3 upgrade - force exit after auth rotation resumes at rotate spending keys nfc tap") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware(cloudStoreAccountForBackup = CloudStoreAccount1Fake)


    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade(app)
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()

      // Complete auth rotation (old W1 tap + new W3 rotation tap with confirmations)
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()
      approveW3ConfirmableNfc() // auth rotation

      // Rotate spending keyset tap starts — wait for its first confirmation screen,
      // proving we're past auth rotation, then force exit.
      approveW3PromptSelectionAndAwaitHardwareConfirmation() // composite auth

      cancelAndIgnoreRemainingEvents()
    }

    // Relaunch
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Resume rewinds to the spending keyset composite tap (PreparingUpgradeAuthorization),
      // NOT back to auth rotation — auth keys are already rotated.
      approveW3ConfirmableNfc() // composite auth

      advanceThroughCloudBackup()

      // Zero balance - sweep skipped
      dismissW3UpgradeCompleteSheet()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }
})

/**
 * Navigate from MoneyHome to the W3 upgrade entry point via SecurityHub -> Device Settings.
 */
private suspend fun ReceiveTurbine<ScreenModel>.navigateToW3Upgrade(app: AppTester) {
  app.w3OnboardingFeatureFlag.setFlagValue(true)
  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()
  awaitUntilBody<SecurityHubBodyModel>()
    .clickBitkeyDevice()
  awaitUntilBody<DeviceSettingsFormBodyModel>(
    matching = { it.onUpgradeDevice != null }
  ).onUpgradeDevice.shouldNotBeNull().invoke()
}

/**
 * Advance through the intro and device ready screens.
 */
private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughIntroPhase() {
  awaitUntilBody<W3UpgradeIntroBodyModel>()
    .clickPrimaryButton()
  awaitUntilBody<W3UpgradeDeviceReadyBodyModel>()
    .clickPrimaryButton()
}

/**
 * Advance through the W3 hardware pairing NFC flow.
 * W3 pairing uses the V2 activation instructions with a two-tap flow:
 * 1. ActivationInstructionsV2 -> first NFC tap (starts enrollment)
 * 2. CompleteTwoTapBodyModel -> second NFC tap (completes enrollment)
 */
private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughPairingPhase() {
  // ActivationInstructionsV2 screen ("Set up your Bitkey") -> triggers first NFC tap
  awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS_V2)
    .clickPrimaryButton()
  // CompleteTwoTap screen ("Finished on your device?") -> triggers second NFC tap
  awaitUntilBody<CompleteTwoTapBodyModel>()
    .clickPrimaryButton()
}

/**
 * Advance through auth key rotation and composite keyset authorization:
 * Old W1 tap (proof of possession) → New W3 auth rotation tap → Composite W3 authorization tap
 * (descriptor backups + keyset activation + DDK) → Hardware descriptor provisioning.
 */
private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughAuthAndKeyRotation() {
  // Old hardware auth rotation: tap old W1 for proof of possession (non-confirmable)
  awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
    .onContinue()

  // New hardware auth rotation: tap new W3 for auth key signatures (confirmable).
  // With fake W3 hardware, each confirmable session shows 2 screens:
  //   1. PromptSelectionFormBodyModel (emulated Approve/Deny prompt)
  //   2. HardwareConfirmationScreenModel (tap-to-confirm)
  awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel>()
    .onContinue()
  approveW3ConfirmableNfc()

  // Composite W3 authorization tap (confirmable): descriptor backups + keyset activation + DDK
  approveW3ConfirmableNfc()

  // Hardware descriptor provisioning (nfcSessionUIStateMachine, not confirmable — no UI prompt)
}

/**
 * Advance through the cloud backup step. Returns the consumed [ScreenModel] so callers
 * can chain into the next step without re-awaiting an emission they may not get.
 *
 * Handles both first-run and resume-after-relaunch paths: when sealedCsek is present
 * SaveBackupInstructions may be skipped, and on resume sealedCsek may be null so the
 * instructions screen is shown. When the wallet was onboarded with a saved cloud
 * account, the upgrade flow updates the existing backup silently and skips both
 * CloudSignInModelFake and SaveBackupInstructionsBodyModel — flow advances directly
 * to the next post-backup screen (W3UpgradeOldHardwareInstructionsBodyModel for funded
 * wallets, MoneyHome with W3UpgradeCompleteSheetBodyModel for zero-balance).
 */
private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCloudBackup(
  cloudStoreAccount: CloudStoreAccount = CloudStoreAccount1Fake,
): ScreenModel {
  val screen = awaitUntilScreenWithBody<BodyModel>(
    matchingScreen = {
      it.body is CloudSignInModelFake ||
        (it.body as? SaveBackupInstructionsBodyModel)?.isLoading == false ||
        it.body is W3UpgradeOldHardwareInstructionsBodyModel ||
        it.bottomSheetModel?.body is W3UpgradeCompleteSheetBodyModel
    }
  )
  when (val body = screen.body) {
    is CloudSignInModelFake -> body.signInSuccess(cloudStoreAccount)
    is SaveBackupInstructionsBodyModel -> body.onBackupClick()
    else -> Unit // already past cloud backup; caller handles via returned screen
  }
  return screen
}

private suspend fun ReceiveTurbine<ScreenModel>.dismissW3UpgradeCompleteSheet() {
  awaitUntilScreenWithBody<MoneyHomeBodyModel>(
    matchingScreen = { it.bottomSheetModel?.body is W3UpgradeCompleteSheetBodyModel }
  ).let { screen ->
    checkNotNull(screen.bottomSheetModel)
      .body
      .shouldBeTypeOf<W3UpgradeCompleteSheetBodyModel>()
      .onDone()
  }
  awaitUntilBody<MoneyHomeBodyModel>()
}

/**
 * Approve a W3 device confirmation prompt. Handles both [HardwareConfirmationScreenModel]
 * (real confirmation flow) and [PromptSelectionFormBodyModel] (emulated prompt flow).
 */
private suspend fun ReceiveTurbine<ScreenModel>.approveW3ConfirmableNfc() {
  awaitUntilScreenWithBody<BodyModel>(
    matchingScreen = { screen ->
      screen.body is HardwareConfirmationScreenModel ||
        screen.bottomSheetModel?.body is PromptSelectionFormBodyModel
    }
  ).let { screen ->
    when {
      screen.bottomSheetModel?.body is PromptSelectionFormBodyModel -> {
        (checkNotNull(screen.bottomSheetModel).body as PromptSelectionFormBodyModel).clickApprove()
        awaitUntilBody<HardwareConfirmationScreenModel> {
          onConfirm()
        }
      }
      screen.body is HardwareConfirmationScreenModel -> {
        (screen.body as HardwareConfirmationScreenModel).onConfirm()
      }
    }
  }
}

private suspend fun ReceiveTurbine<ScreenModel>.approveW3PromptSelectionAndAwaitHardwareConfirmation() {
  awaitUntilScreenWithBody<BodyModel>(
    matchingScreen = { screen ->
      screen.bottomSheetModel?.body is PromptSelectionFormBodyModel
    }
  ).let { screen ->
    (checkNotNull(screen.bottomSheetModel).body as PromptSelectionFormBodyModel).clickApprove()
  }

  awaitUntilBody<HardwareConfirmationScreenModel>()
}

/**
 * Verify post-upgrade state: account config, keybox, and device info all reflect W3.
 */
private suspend fun AppTester.verifyPostW3UpgradeState() {
  accountService.activeAccount().test {
    val account = awaitItem()
    account.shouldBeTypeOf<FullAccount>()
    account.keybox.canUseKeyboxKeysets.shouldBeTrue()
    account.config.shouldBeTypeOf<FullAccountConfig>()
      .hardwareType.shouldBe(HardwareType.W3)
  }

  val deviceInfo = firmwareDeviceInfoDao.getDeviceInfo().getOrThrow()
  deviceInfo.shouldNotBeNull()
  deviceInfo.hardwareType().shouldBe(HardwareType.W3)
}
