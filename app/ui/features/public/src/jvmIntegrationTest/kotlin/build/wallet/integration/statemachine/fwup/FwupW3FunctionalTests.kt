package build.wallet.integration.statemachine.fwup

import app.cash.turbine.ReceiveTurbine
import bitkey.account.HardwareType
import bitkey.notifications.NotificationChannel
import bitkey.notifications.NotificationPreferences
import bitkey.securitycenter.SecurityActionRecommendation.ENABLE_EMAIL_NOTIFICATIONS
import bitkey.securitycenter.SecurityActionRecommendation.ENABLE_PUSH_NOTIFICATIONS
import bitkey.securitycenter.SecurityActionRecommendation.UPDATE_FIRMWARE
import bitkey.ui.screens.securityhub.SecurityHubBodyModel
import build.wallet.bitkey.f8e.FullAccountId
import build.wallet.cloud.store.CloudStoreAccountFake.Companion.CloudStoreAccount1Fake
import build.wallet.firmware.FirmwareDeviceInfo
import build.wallet.firmware.FirmwareMetadata
import build.wallet.firmware.McuInfo
import build.wallet.firmware.McuName
import build.wallet.firmware.McuRole
import build.wallet.firmware.SecureBootConfig
import build.wallet.fwup.FirmwareData.FirmwareUpdateState.PendingUpdate
import build.wallet.statemachine.core.test
import build.wallet.statemachine.fwup.FwupNfcBodyModel
import build.wallet.statemachine.moneyhome.MoneyHomeBodyModel
import build.wallet.statemachine.nfc.FwupInstructionsBodyModel
import build.wallet.statemachine.nfc.PromptSelectionFormBodyModel
import build.wallet.statemachine.ui.awaitUntilBody
import build.wallet.testing.AppTester
import build.wallet.testing.AppTester.Companion.launchNewApp
import build.wallet.testing.ext.onboardFullAccountWithFakeHardware
import com.github.michaelbull.result.getOrThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.core.test.TestScope
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeTypeOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/**
 * Integration tests for W3 firmware update (FWUP) functionality.
 *
 * Tests the full FWUP flow using real UI state machines with BitkeyW3CommandsFake,
 * including the W3-specific two-tap NFC flow with emulated device confirmation prompts.
 */
class FwupW3FunctionalTests : FunSpec({

  test("W3 FWUP - Success / approval") {
    val app = launchW3AppWithPendingUpdate()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 30.seconds,
      turbineTimeout = 10.seconds
    ) {
      navigateToSecurityHub()
      clickFirmwareUpdateRecommendation()
      startFirmwareUpdate()
      awaitNfcSearching()
      approveW3DeviceConfirmation() // First MCU (UXC)
      approveW3DeviceConfirmation() // Second MCU (CORE)
      awaitFwupSuccess()
      awaitReturnToSecurityHub()
      cancelAndIgnoreRemainingEvents()
    }
  }

  test("W3 FWUP - user denies on device confirmation") {
    val app = launchW3AppWithPendingUpdate()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 30.seconds,
      turbineTimeout = 10.seconds
    ) {
      navigateToSecurityHub()
      clickFirmwareUpdateRecommendation()
      startFirmwareUpdate()
      awaitNfcSearching()
      denyW3DeviceConfirmation()
      awaitReturnToInstructions()
      cancelAndIgnoreRemainingEvents()
    }

    app.firmwareDataService.firmwareData().value
      .firmwareUpdateState.shouldBeTypeOf<PendingUpdate>()
  }

  test("W3 FWUP - user approves first MCU but denies second") {
    val app = launchW3AppWithPendingUpdate()

    app.appUiStateMachine.test(
      props = Unit,
      testTimeout = 30.seconds,
      turbineTimeout = 10.seconds
    ) {
      navigateToSecurityHub()
      clickFirmwareUpdateRecommendation()
      startFirmwareUpdate()
      awaitNfcSearching()
      approveW3DeviceConfirmation() // Approve CORE
      denyW3DeviceConfirmation() // Deny UXC
      awaitReturnToInstructions()
      cancelAndIgnoreRemainingEvents()
    }

    // Update should still be pending since second MCU was denied
    app.firmwareDataService.firmwareData().value
      .firmwareUpdateState.shouldBeTypeOf<PendingUpdate>()
  }
})

/** Type alias for the turbine test context. */
private typealias TestContext = ReceiveTurbine<build.wallet.statemachine.core.ScreenModel>

private suspend fun TestContext.navigateToSecurityHub() {
  awaitUntilBody<MoneyHomeBodyModel>()
  awaitUntilBody<MoneyHomeBodyModel> {
    onSecurityHubTabClick()
  }
}

private suspend fun TestContext.clickFirmwareUpdateRecommendation() {
  awaitUntilBody<SecurityHubBodyModel>(
    matching = {
      it.recommendations.any { rec -> rec == UPDATE_FIRMWARE } ||
        it.atRiskRecommendations.isNotEmpty()
    }
  ) {
    if (atRiskRecommendations.isNotEmpty()) {
      error("At-risk recommendations block firmware recommendation: $atRiskRecommendations")
    }
    onRecommendationClick(UPDATE_FIRMWARE)
  }
}

private suspend fun TestContext.startFirmwareUpdate() {
  awaitUntilBody<FwupInstructionsBodyModel> {
    headerModel.headline.shouldBe("Update your device")
    buttonModel.text.shouldBe("Update Bitkey")
    buttonModel.onClick.invoke()
  }
}

private suspend fun TestContext.awaitNfcSearching() {
  awaitUntilBody<FwupNfcBodyModel> {
    status.shouldBeTypeOf<FwupNfcBodyModel.Status.Searching>()
  }
}

/**
 * Approves the W3 device confirmation prompt.
 * W3 hardware requires on-device confirmation for each MCU update.
 */
private suspend fun TestContext.approveW3DeviceConfirmation() {
  awaitUntilBody<PromptSelectionFormBodyModel> {
    options.shouldBe(listOf("Approve", "Deny"))
    onOptionSelected(0)
  }
}

private suspend fun TestContext.denyW3DeviceConfirmation() {
  awaitUntilBody<PromptSelectionFormBodyModel> {
    options.shouldBe(listOf("Approve", "Deny"))
    onOptionSelected(1)
  }
}

private suspend fun TestContext.awaitFwupSuccess() {
  awaitUntilBody<FwupNfcBodyModel>(
    matching = { it.status is FwupNfcBodyModel.Status.Success }
  ) {
    status.shouldBeTypeOf<FwupNfcBodyModel.Status.Success>()
  }
}

private suspend fun TestContext.awaitReturnToSecurityHub() {
  awaitUntilBody<SecurityHubBodyModel>()
}

private suspend fun TestContext.awaitReturnToInstructions() {
  awaitUntilBody<FwupInstructionsBodyModel>()
}

/**
 * Launches an app configured for W3 hardware with a pending firmware update.
 */
private suspend fun TestScope.launchW3AppWithPendingUpdate(): AppTester {
  val app = launchNewApp()

  app.accountConfigService.setHardwareType(HardwareType.W3).getOrThrow()

  val account = app.onboardFullAccountWithFakeHardware(
    cloudStoreAccountForBackup = CloudStoreAccount1Fake,
    shouldSetUpNotifications = true
  )

  app.configureNotificationPreferences(account.accountId)
  app.awaitNotificationRecommendationsCleared()
  app.configureW3FirmwareUpdate()

  return app
}

/**
 * Configures notification preferences to prevent notification-related recommendations
 * from appearing as at-risk (which would hide UPDATE_FIRMWARE).
 */
private suspend fun AppTester.configureNotificationPreferences(accountId: FullAccountId) {
  val preferences = NotificationPreferences(
    moneyMovement = setOf(NotificationChannel.Email, NotificationChannel.Push),
    productMarketing = emptySet(),
    accountSecurity = setOf(NotificationChannel.Email, NotificationChannel.Push)
  )
  notificationsPreferencesCachedProvider.updateNotificationsPreferences(
    accountId = accountId,
    preferences = preferences,
    hwFactorProofOfPossession = null
  ).getOrThrow()
}

/**
 * Waits for notification-related recommendations to be cleared from at-risk list.
 *
 * This function waits for TWO conditions to be met:
 * 1. State is non-null - ensures we have real data, not placeholder values
 * 2. At-risk recommendations don't contain notification-related items
 *
 * The null check is critical because SecurityActionsService initially emits null
 * before real data loads. Without this check, the test could pass prematurely
 * and then fail when real data arrives.
 */
private suspend fun AppTester.awaitNotificationRecommendationsCleared() {
  withTimeout(5.seconds) {
    securityActionsService.securityActionsWithRecommendations
      .first { actions ->
        // Must wait for real data to be loaded (non-null), not just any emission
        actions != null &&
          !actions.atRiskRecommendations.contains(ENABLE_EMAIL_NOTIFICATIONS) &&
          !actions.atRiskRecommendations.contains(ENABLE_PUSH_NOTIFICATIONS)
      }
  }
}

/**
 * Configures W3 device info and triggers firmware sync.
 * FirmwareDownloaderFake will automatically generate the firmware manifest and files
 * since the test uses fake hardware (via onboardFullAccountWithFakeHardware).
 */
private suspend fun AppTester.configureW3FirmwareUpdate() {
  firmwareDeviceInfoDao.setDeviceInfo(TestW3FirmwareDeviceInfo).getOrThrow()

  // Trigger firmware sync and wait for pending update to be detected
  // FirmwareDownloaderFake will generate W3 manifest files automatically
  firmwareDataService.syncLatestFwupData()

  withTimeout(5.seconds) {
    firmwareDataService.firmwareData()
      .first { it.firmwareUpdateState is PendingUpdate }
  }
}

/**
 * W3 firmware device info for testing.
 * Uses a non-fake serial so FirmwareDataServiceImpl.syncLatestFwupData() actually runs.
 */
private val TestW3FirmwareDeviceInfo = FirmwareDeviceInfo(
  version = "1.2.3",
  serial = "test-w3-serial",
  swType = "dev",
  hwRevision = "w3a-core-evt",
  activeSlot = FirmwareMetadata.FirmwareSlot.B,
  batteryCharge = 89.45,
  vCell = 1000,
  avgCurrentMa = 1234,
  batteryCycles = 1234,
  secureBootConfig = SecureBootConfig.PROD,
  timeRetrieved = 1691787589,
  bioMatchStats = null,
  mcuInfo = listOf(
    McuInfo(mcuRole = McuRole.CORE, mcuName = McuName.EFR32, firmwareVersion = "1.2.3"),
    McuInfo(mcuRole = McuRole.UXC, mcuName = McuName.STM32U5, firmwareVersion = "1.2.3")
  )
)
