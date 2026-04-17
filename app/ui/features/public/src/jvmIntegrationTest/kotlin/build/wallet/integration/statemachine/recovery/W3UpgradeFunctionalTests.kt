package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import app.cash.turbine.test
import bitkey.account.FullAccountConfig
import bitkey.account.HardwareType
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2
import build.wallet.bitkey.account.FullAccount
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
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
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.statemachine.walletmigration.*
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.ext.addSomeFunds
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import build.wallet.testing.ext.returnFundsToTreasury
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
    app.onboardFullAccountWithFakeHardware()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade()
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      advanceThroughCloudBackup()

      // Zero balance - sweep is skipped entirely
      awaitUntilBody<W3UpgradeCompleteBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }

  test("W3 upgrade happy path - sweep real funds") {
    val app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware()
    app.addSomeFunds(sats(10_000L))

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade()
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      advanceThroughCloudBackup()

      // Has funds - sweep flow
      awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel>()
        .onContinue()
      awaitUntilBody<TransferConfirmationScreenModel>()
        .clickPrimaryButton()
      awaitUntilBody<TransferInitiatedBodyModel>()
        .clickPrimaryButton()

      awaitUntilBody<W3UpgradeCompleteBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
    app.waitForFunds()
    app.returnFundsToTreasury()
  }

  test("W3 upgrade - force exit during auth rotation resumes at auth rotation") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade()
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()

      // Wait for auth rotation instructions to appear, then force exit
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    // Relaunch - MoneyHome auto-detects in-progress W3 upgrade
    app = app.relaunchApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      // Resumes at auth rotation (GeneratingAuthKeys -> old HW instructions)
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()

      // Complete the rest of the auth rotation
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()
      // Approve W3 device confirmations (2 per confirmable session × 2 sessions)
      approveW3Confirmation() // auth rotation: emulated prompt
      approveW3Confirmation() // auth rotation: tap-to-confirm
      approveW3Confirmation() // composite auth: emulated prompt
      approveW3Confirmation() // composite auth: tap-to-confirm

      advanceThroughCloudBackup()

      // Zero balance - sweep skipped
      awaitUntilBody<W3UpgradeCompleteBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }

  test("W3 upgrade - force exit during cloud backup resumes (zero balance)") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade()
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
      // backup may or may not have persisted. Handle both resume paths.
      val body = awaitUntilBody<BodyModel>(
        matching = {
          it is CloudSignInModelFake ||
            it is SaveBackupInstructionsBodyModel ||
            it is W3UpgradeCompleteBodyModel
        }
      )
      when (body) {
        is SaveBackupInstructionsBodyModel -> {
          body.onBackupClick()
          awaitUntilBody<CloudSignInModelFake>()
            .signInSuccess(CloudStoreAccount1Fake)
          // Cloud backup re-done on resume; still need to reach complete
          awaitUntilBody<W3UpgradeCompleteBodyModel>()
            .onDone()
        }
        is CloudSignInModelFake -> {
          body.signInSuccess(CloudStoreAccount1Fake)
          awaitUntilBody<W3UpgradeCompleteBodyModel>()
            .onDone()
        }
        is W3UpgradeCompleteBodyModel -> {
          // Backup already completed before exit; resume landed past cloud backup
          body.onDone()
        }
      }

      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }

  test("W3 upgrade - force exit after cloud backup resumes at sweep with funds") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware()
    app.addSomeFunds(sats(10_000L))

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade()
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()
      advanceThroughAuthAndKeyRotation()
      advanceThroughCloudBackup()

      // Wait for the sweep instructions to confirm backup completed, then force exit
      awaitUntilBody<W3UpgradeOldHardwareInstructionsBodyModel>()

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

      awaitUntilBody<W3UpgradeCompleteBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
    app.waitForFunds()
    app.returnFundsToTreasury()
  }

  test("W3 upgrade - force exit after auth rotation resumes at rotate spending keys nfc tap") {
    var app = launchLegacyWalletApp()
    app.onboardFullAccountWithFakeHardware()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 120.seconds,
      turbineTimeout = 60.seconds
    ) {
      navigateToW3Upgrade()
      advanceThroughIntroPhase()
      advanceThroughPairingPhase()

      // Complete auth rotation (old W1 tap + new W3 rotation tap with confirmations)
      awaitUntilBody<W3UpgradeOldHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()
      awaitUntilBody<W3UpgradeNewHardwareAuthRotationInstructionsBodyModel>()
        .onContinue()
      approveW3Confirmation() // auth rotation: emulated prompt
      approveW3Confirmation() // auth rotation: tap-to-confirm

      // Rotate spending keyset tap starts — wait for its first confirmation screen,
      // proving we're past auth rotation, then force exit.
      approveW3Confirmation() // composite auth: emulated prompt

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
      approveW3Confirmation() // composite auth: emulated prompt
      approveW3Confirmation() // composite auth: tap-to-confirm

      advanceThroughCloudBackup()

      // Zero balance - sweep skipped
      awaitUntilBody<W3UpgradeCompleteBodyModel>()
        .onDone()

      awaitUntilBody<MoneyHomeBodyModel>()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostW3UpgradeState()
  }
})

/**
 * Navigate from MoneyHome to the W3 upgrade entry point via SecurityHub -> Device Settings.
 */
private suspend fun ReceiveTurbine<ScreenModel>.navigateToW3Upgrade() {
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
  approveW3Confirmation() // emulated prompt
  approveW3Confirmation() // tap-to-confirm

  // Composite W3 authorization tap (confirmable): descriptor backups + keyset activation + DDK
  approveW3Confirmation() // emulated prompt
  approveW3Confirmation() // tap-to-confirm

  // Hardware descriptor provisioning (nfcSessionUIStateMachine, not confirmable — no UI prompt)
}

/**
 * Advance through the cloud backup step.
 * On first run with sealedCsek present, SaveBackupInstructions may be skipped.
 * On resume after relaunch, sealedCsek may be null so SaveBackupInstructions is shown.
 * This helper handles both cases.
 */
private suspend fun ReceiveTurbine<ScreenModel>.advanceThroughCloudBackup() {
  // CloudSignInModelFake extends BodyModel (not FormBodyModel), while
  // SaveBackupInstructionsBodyModel extends FormBodyModel. Await either.
  awaitUntilBody<BodyModel>(
    matching = { it is CloudSignInModelFake || it is SaveBackupInstructionsBodyModel }
  ).let { body ->
    when (body) {
      is CloudSignInModelFake -> body.signInSuccess(CloudStoreAccount1Fake)
      is SaveBackupInstructionsBodyModel -> {
        body.onBackupClick()
        awaitUntilBody<CloudSignInModelFake>()
          .signInSuccess(CloudStoreAccount1Fake)
      }
    }
  }
}

/**
 * Approve a W3 device confirmation prompt. Handles both [HardwareConfirmationScreenModel]
 * (real confirmation flow) and [PromptSelectionFormBodyModel] (emulated prompt flow).
 */
private suspend fun ReceiveTurbine<ScreenModel>.approveW3Confirmation() {
  awaitUntilBody<FormBodyModel>(
    matching = { it is HardwareConfirmationScreenModel || it is PromptSelectionFormBodyModel }
  ).let { body ->
    when (body) {
      is HardwareConfirmationScreenModel -> body.onConfirm()
      is PromptSelectionFormBodyModel -> body.clickApprove()
    }
  }
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
