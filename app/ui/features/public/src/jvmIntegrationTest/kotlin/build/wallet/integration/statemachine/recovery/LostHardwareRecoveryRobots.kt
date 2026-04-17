package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_ACTIVATION_INSTRUCTIONS_V2
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_COMPLETE_TWO_TAP
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.HW_SAVE_FINGERPRINT_INSTRUCTIONS
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.testing.AppTester
import build.wallet.testing.ext.awaitNoActiveRecovery

/**
 * Navigates the W1 lost-hardware recovery initiation flow from MoneyHome through to
 * the D&N server-recovery loading screen.
 *
 * W1 fakes return FingerprintEnrollmentStarted on first tap, so the legacy fingerprint
 * enrollment screen appears before the second NFC tap.
 */
suspend fun ReceiveTurbine<ScreenModel>.startRecoveryAndAdvanceToDelayNotify(
  app: AppTester,
) {
  app.awaitNoActiveRecovery()

  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()
  awaitUntilBody<SecurityHubBodyModel>()
    .clickBitkeyDevice()

  awaitUntilBody<DeviceSettingsFormBodyModel>()
    .onReplaceDevice()
  awaitUntilBody<HardwareReplacementInstructionsModel>()
    .onContinue()
  awaitUntilBody<NewDeviceReadyQuestionBodyModel>()
    .clickPrimaryButton()
  // V2 pairing flow: ActivationInstructionsV2 → NFC → fingerprint enrollment → NFC → done
  awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS_V2)
    .clickPrimaryButton()
  awaitUntilBody<PairNewHardwareBodyModel>(HW_SAVE_FINGERPRINT_INSTRUCTIONS)
    .clickPrimaryButton()
  awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
}

/**
 * W3 variant of [startRecoveryAndAdvanceToDelayNotify].
 * W3 fake hardware completes fingerprint enrollment in a single NFC tap (CompleteTwoTap flow).
 */
suspend fun ReceiveTurbine<ScreenModel>.startW3RecoveryAndAdvanceToDelayNotify(
  app: AppTester,
) {
  app.awaitNoActiveRecovery()

  awaitUntilBody<MoneyHomeBodyModel>()
    .onSecurityHubTabClick()
  awaitUntilBody<SecurityHubBodyModel>()
    .clickBitkeyDevice()

  awaitUntilBody<DeviceSettingsFormBodyModel>()
    .onReplaceDevice()
  awaitUntilBody<HardwareReplacementInstructionsModel>()
    .onContinue()
  awaitUntilBody<NewDeviceReadyQuestionBodyModel>()
    .clickPrimaryButton()
  // V2 pairing flow: ActivationInstructionsV2 → NFC → CompleteTwoTap → NFC → done
  awaitUntilBody<PairNewHardwareBodyModel>(HW_ACTIVATION_INSTRUCTIONS_V2)
    .clickPrimaryButton()
  awaitUntilBody<CompleteTwoTapBodyModel>(HW_COMPLETE_TWO_TAP)
    .clickPrimaryButton()
  awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_INITIATION_INITIATING_SERVER_RECOVERY)
}
