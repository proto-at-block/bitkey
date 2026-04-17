package build.wallet.integration.statemachine.recovery

import app.cash.turbine.ReceiveTurbine
import bitkey.recovery.fundslost.AtRiskCause
import bitkey.recovery.fundslost.FundsLostRiskLevel
import bitkey.recovery.fundslost.FundsLostRiskLevel.AtRisk
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.analytics.events.screen.id.HardwareRecoveryEventTrackerScreenId.*
import build.wallet.analytics.events.screen.id.PairHardwareEventTrackerScreenId.*
import build.wallet.availability.AppFunctionalityStatus.FullFunctionality
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.money.BitcoinMoney.Companion.sats
import build.wallet.feature.setFlagValue
import build.wallet.statemachine.account.create.full.hardware.CompleteTwoTapBodyModel
import build.wallet.statemachine.account.create.full.hardware.PairNewHardwareBodyModel
import build.wallet.statemachine.cloud.CloudSignInModelFake
import build.wallet.statemachine.cloud.SaveBackupInstructionsBodyModel
import bitkey.account.HardwareType
import build.wallet.statemachine.core.BodyModel
import build.wallet.statemachine.core.ScreenModel
import build.wallet.statemachine.core.test
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.send.hardwareconfirmation.HardwareConfirmationScreenModel
import build.wallet.statemachine.recovery.hardware.initiating.HardwareReplacementInstructionsModel
import build.wallet.statemachine.recovery.hardware.initiating.NewDeviceReadyQuestionBodyModel
import build.wallet.statemachine.recovery.inprogress.DelayAndNotifyNewKeyReady
import build.wallet.statemachine.recovery.sweep.ZeroBalancePromptBodyModel
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.statemachine.ui.awaitUntilScreenWithBody
import build.wallet.statemachine.ui.clickPrimaryButton
import build.wallet.statemachine.ui.robots.awaitLoadingScreen
import build.wallet.statemachine.ui.robots.clickBitkeyDevice
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchLegacyWalletApp
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.integration.statemachine.recovery.socrec.awaitW3ConfirmableNfcSession
import build.wallet.testing.ext.*
import build.wallet.ui.model.alert.ButtonAlertModel
import build.wallet.ui.model.status.BannerStyle
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class LostHardwareRecoveryFunctionalTests : FunSpec({
  suspend fun AppTester.prepareApp(delayNotifyDuration: Duration = 1.seconds) {
    onboardFullAccountWithFakeHardware(delayNotifyDuration = delayNotifyDuration)
    fakeNfcCommands.wipeDevice()
  }

  testForLegacyAndPrivateWallet("wallet at risk banner after wiping hardware with zero balance") { app ->
    app.onboardFullAccountWithFakeHardware()
    app.shouldHaveTotalBalance(sats(0))
    app.fakeNfcCommands.wipeDevice()
    (app.fundsRiskLossService.riskLevel() as? MutableStateFlow<FundsLostRiskLevel>)
      ?.let { it.value = AtRisk(cause = AtRiskCause.MissingHardware) }
    withTimeout(20.seconds) {
      app.appFunctionalityService.status.first { it is FullFunctionality }
    }

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      val screen = awaitUntilScreenWithBody<MoneyHomeBodyModel>(
        matchingScreen = { it.statusBannerModel?.title == "Your wallet is at risk" }
      )

      screen.statusBannerModel.shouldNotBeNull().apply {
        title.shouldBe("Your wallet is at risk")
        subtitle.shouldBe("Add a Bitkey device to avoid losing funds →")
        style.shouldBe(BannerStyle.Destructive)
      }

      cancelAndIgnoreRemainingEvents()
    }
  }

  testForLegacyAndPrivateWallet("lost hardware recovery - happy path") { app ->
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()
      awaitUntilBody<SecurityHubBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.verifyPostRecoveryState()
  }

  testForLegacyAndPrivateWallet("cancel initiated recovery") { initialApp ->
    val app = initialApp
    app.prepareApp()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      startRecoveryAndAdvanceToDelayNotify(app)

      // Cancel recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>(
        matching = { it.onStopRecovery != null }
      ).onStopRecovery.shouldNotBeNull().invoke()

      awaitUntilScreenWithBody<DelayAndNotifyNewKeyReady>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }

      awaitUntilBody<SecurityHubBodyModel>()

      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }
  }

  // --- W3 Hardware Transition Tests ---

  testForRecoveryTransitions(
    "lost hardware recovery - happy path",
    transitions = listOf(RecoveryHardwareTransition.W1ToW3, RecoveryHardwareTransition.W3ToW3)
  ) { transition ->
    val app = when (transition.appMode) {
      AppMode.Legacy -> launchLegacyWalletApp()
      AppMode.Private -> launchNewApp()
    }
    app.onboardFullAccountWithFakeHardware(
      hardwareType = transition.sourceHardwareType
    )
    // Wipe the correct hardware type (W3 uses separate fake NFC commands)
    if (transition.sourceHardwareType == HardwareType.W3) {
      app.fakeW3NfcCommands.wipeDevice()
    } else {
      app.fakeNfcCommands.wipeDevice()
    }
    // Set replacement hardware type for the recovery flow
    app.defaultAccountConfigService.setHardwareType(transition.replacementHardwareType)
    // Both always enabled in production; needed for W1→W3 so server creates V2 keyset
    app.chaincodeDelegationFeatureFlag.setFlagValue(true)
    app.updateToPrivateWalletOnRecoveryFeatureFlag.setFlagValue(true)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      startW3RecoveryAndAdvanceToDelayNotify(app)

      // Complete recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>()
        .onCompleteRecovery()
      // W3: confirmable NFC session for spending key creation
      awaitW3ConfirmableNfcSession()
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
      // W3: confirmable NFC session for DDK upload
      awaitW3ConfirmableNfcSession()
      awaitUntilBody<SaveBackupInstructionsBodyModel>()
        .onBackupClick()
      awaitUntilBody<CloudSignInModelFake>()
        .signInSuccess(CloudStoreAccount1Fake)
      awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
      awaitUntilBody<ZeroBalancePromptBodyModel>()
        .onDone()
      awaitUntilBody<SecurityHubBodyModel>()
      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }

    app.assertActiveHardwareType(transition.replacementHardwareType)
    app.verifyPostRecoveryState()
  }

  testForRecoveryTransitions(
    "cancel initiated recovery",
    transitions = listOf(RecoveryHardwareTransition.W3ToW3)
  ) { transition ->
    val app = launchNewApp()
    app.onboardFullAccountWithFakeHardware(hardwareType = transition.sourceHardwareType)
    app.fakeW3NfcCommands.wipeDevice()
    app.defaultAccountConfigService.setHardwareType(transition.replacementHardwareType)

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 60.seconds,
      turbineTimeout = 60.seconds
    ) {
      startW3RecoveryAndAdvanceToDelayNotify(app)

      // Cancel recovery
      awaitUntilBody<DelayAndNotifyNewKeyReady>(
        matching = { it.onStopRecovery != null }
      ).onStopRecovery.shouldNotBeNull().invoke()

      awaitUntilScreenWithBody<DelayAndNotifyNewKeyReady>(
        matchingScreen = { it.alertModel != null }
      ) {
        alertModel.shouldBeTypeOf<ButtonAlertModel>()
          .onPrimaryButtonClick()
      }

      awaitUntilBody<SecurityHubBodyModel>()

      app.awaitNoActiveRecovery()

      cancelAndIgnoreRemainingEvents()
    }
  }
})

suspend fun AppTester.performLostHardwareRecovery() {
  appUiStateMachine.test(
    props = Unit,
    testTimeout = 60.seconds,
    turbineTimeout = 60.seconds
  ) {
    startRecoveryAndAdvanceToDelayNotify(this@performLostHardwareRecovery)

    // Complete recovery
    awaitUntilBody<DelayAndNotifyNewKeyReady>()
      .onCompleteRecovery()
    awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_ROTATING_AUTH_KEYS)
    awaitUntilBody<SaveBackupInstructionsBodyModel>()
      .onBackupClick()
    awaitUntilBody<CloudSignInModelFake>()
      .signInSuccess(CloudStoreAccount1Fake)
    awaitLoadingScreen(LOST_HW_DELAY_NOTIFY_SWEEP_GENERATING_PSBTS)
    awaitUntilBody<ZeroBalancePromptBodyModel>()
      .onDone()

    awaitUntilBody<SecurityHubBodyModel>()
    awaitNoActiveRecovery()

    cancelAndIgnoreRemainingEvents()
  }
}
