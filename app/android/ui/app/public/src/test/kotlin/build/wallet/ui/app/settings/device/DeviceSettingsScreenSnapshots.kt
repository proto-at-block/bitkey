package build.wallet.ui.app.settings.device

import build.wallet.analytics.events.screen.id.SettingsEventTrackerScreenId
import build.wallet.kotest.paparazzi.paparazziExtension
import build.wallet.statemachine.settings.full.device.DeviceSettingsFormBodyModel
import build.wallet.ui.app.core.form.FormScreen
import io.kotest.core.spec.style.FunSpec

class DeviceSettingsScreenSnapshots : FunSpec({

  val paparazzi = paparazziExtension()

  test("device settings - update available") {
    paparazzi.snapshot {
      FormScreen(
        model =
          DeviceSettingsFormBodyModel(
            emptyState = false,
            modelName = "Bitkey",
            trackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO,
            modelNumber = "MWHC2LL/A",
            serialNumber = "G6TZ64NNN70Q",
            lastSyncDate = "07/12/23 at 9:13am",
            replacementPending = null,
            currentVersion = "1.0.6",
            updateVersion = "1.0.7",
            deviceCharge = "30%",
            replaceDeviceEnabled = true,
            onReplaceDevice = {},
            onBack = {},
            onUpdateVersion = {},
            onSyncDeviceInfo = {},
            onManageReplacement = {},
            resetDeviceEnabled = false,
            onManageFingerprints = {},
            onResetDevice = {},
            coachmark = null
          )
      )
    }
  }

  test("device settings - no update available") {
    paparazzi.snapshot {
      FormScreen(
        model =
          DeviceSettingsFormBodyModel(
            emptyState = false,
            modelName = "Bitkey",
            trackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO,
            modelNumber = "MWHC2LL/A",
            serialNumber = "G6TZ64NNN70Q",
            lastSyncDate = "07/12/23 at 9:13am",
            replacementPending = null,
            currentVersion = "1.0.6",
            updateVersion = null,
            deviceCharge = "70%",
            replaceDeviceEnabled = true,
            onReplaceDevice = {},
            onBack = {},
            onUpdateVersion = {},
            onSyncDeviceInfo = {},
            onManageReplacement = {},
            resetDeviceEnabled = false,
            onManageFingerprints = {},
            onResetDevice = {},
            coachmark = null
          )
      )
    }
  }

  test("device settings - replacement pending") {
    paparazzi.snapshot {
      FormScreen(
        model =
          DeviceSettingsFormBodyModel(
            emptyState = false,
            modelName = "Bitkey",
            trackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO,
            modelNumber = "MWHC2LL/A",
            serialNumber = "G6TZ64NNN70Q",
            lastSyncDate = "07/12/23 at 9:13am",
            replacementPending = "7 days",
            currentVersion = "1.0.6",
            updateVersion = null,
            deviceCharge = "70%",
            replaceDeviceEnabled = true,
            onReplaceDevice = {},
            onBack = {},
            onUpdateVersion = {},
            onSyncDeviceInfo = {},
            onManageReplacement = {},
            resetDeviceEnabled = false,
            onManageFingerprints = {},
            onResetDevice = {},
            coachmark = null
          )
      )
    }
  }

  test("device settings - replacement pending and update pending") {
    paparazzi.snapshot {
      FormScreen(
        model =
          DeviceSettingsFormBodyModel(
            emptyState = false,
            modelName = "Bitkey",
            trackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO,
            modelNumber = "MWHC2LL/A",
            serialNumber = "G6TZ64NNN70Q",
            lastSyncDate = "07/12/23 at 9:13am",
            replacementPending = "7 days",
            currentVersion = "1.0.6",
            updateVersion = "1.0.7",
            deviceCharge = "70%",
            replaceDeviceEnabled = true,
            onReplaceDevice = {},
            onBack = {},
            onUpdateVersion = {},
            onSyncDeviceInfo = {},
            onManageReplacement = {},
            resetDeviceEnabled = false,
            onManageFingerprints = {},
            onResetDevice = {},
            coachmark = null
          )
      )
    }
  }

  test("device settings - when device is not found") {
    paparazzi.snapshot {
      FormScreen(
        model =
          DeviceSettingsFormBodyModel(
            emptyState = true,
            modelName = "-",
            trackerScreenId = SettingsEventTrackerScreenId.SETTINGS_DEVICE_INFO_EMPTY,
            modelNumber = "-",
            serialNumber = "-",
            lastSyncDate = "-",
            replacementPending = null,
            currentVersion = "-",
            updateVersion = null,
            deviceCharge = "-",
            replaceDeviceEnabled = true,
            onReplaceDevice = {},
            onBack = {},
            onUpdateVersion = {},
            onSyncDeviceInfo = {},
            onManageReplacement = {},
            resetDeviceEnabled = false,
            onManageFingerprints = {},
            onResetDevice = {},
            coachmark = null
          )
      )
    }
  }
})
