import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class DeviceSettingsSnapshotTests: XCTestCase {

    func test_device_settings_with_update_available() {
        let view = FormView(
            viewModel: DeviceSettingsFormBodyModelKt.DeviceSettingsFormBodyModel(
                trackerScreenId: SettingsEventTrackerScreenId.settingsDeviceInfo,
                emptyState: false,
                modelName: "Bitkey",
                currentVersion: "1.0.6",
                updateVersion: "1.0.7",
                modelNumber: "MWHC2LL/A",
                serialNumber: "G6TZ64NNN70Q",
                deviceCharge: "70%",
                lastSyncDate: "07/12/23 at 9:13am",
                replacementPending: nil,
                replaceDeviceEnabled: true,
                onUpdateVersion: {},
                onSyncDeviceInfo: {},
                onReplaceDevice: {},
                onManageReplacement: {},
                onResetDevice: {},
                onBack: {},
                multipleFingerprintsEnabled: false,
                resetDeviceEnabled: false,
                onManageFingerprints: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_device_settings_with_no_update() {
        let view = FormView(
            viewModel: DeviceSettingsFormBodyModelKt.DeviceSettingsFormBodyModel(
                trackerScreenId: SettingsEventTrackerScreenId.settingsDeviceInfo,
                emptyState: false,
                modelName: "Bitkey",
                currentVersion: "1.0.6",
                updateVersion: nil,
                modelNumber: "MWHC2LL/A",
                serialNumber: "G6TZ64NNN70Q",
                deviceCharge: "30%",
                lastSyncDate: "07/12/23 at 9:13am",
                replacementPending: nil,
                replaceDeviceEnabled: true,
                onUpdateVersion: {},
                onSyncDeviceInfo: {},
                onReplaceDevice: {},
                onManageReplacement: {},
                onResetDevice: {},
                onBack: {},
                multipleFingerprintsEnabled: false,
                resetDeviceEnabled: false,
                onManageFingerprints: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_device_settings_replacement_pending() {
        let view = FormView(
            viewModel: DeviceSettingsFormBodyModelKt.DeviceSettingsFormBodyModel(
                trackerScreenId: SettingsEventTrackerScreenId.settingsDeviceInfo,
                emptyState: false,
                modelName: "Bitkey",
                currentVersion: "1.0.6",
                updateVersion: nil,
                modelNumber: "MWHC2LL/A",
                serialNumber: "G6TZ64NNN70Q",
                deviceCharge: "30%",
                lastSyncDate: "07/12/23 at 9:13am",
                replacementPending: "7 days",
                replaceDeviceEnabled: true,
                onUpdateVersion: {},
                onSyncDeviceInfo: {},
                onReplaceDevice: {},
                onManageReplacement: {},
                onResetDevice: {},
                onBack: {},
                multipleFingerprintsEnabled: false,
                resetDeviceEnabled: false,
                onManageFingerprints: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_device_settings_replacement_pending_and_update() {
        let view = FormView(
            viewModel: DeviceSettingsFormBodyModelKt.DeviceSettingsFormBodyModel(
                trackerScreenId: SettingsEventTrackerScreenId.settingsDeviceInfo,
                emptyState: false,
                modelName: "Bitkey",
                currentVersion: "1.0.6",
                updateVersion: "1.0.7",
                modelNumber: "MWHC2LL/A",
                serialNumber: "G6TZ64NNN70Q",
                deviceCharge: "30%",
                lastSyncDate: "07/12/23 at 9:13am",
                replacementPending: "7 days",
                replaceDeviceEnabled: true,
                onUpdateVersion: {},
                onSyncDeviceInfo: {},
                onReplaceDevice: {},
                onManageReplacement: {},
                onResetDevice: {},
                onBack: {},
                multipleFingerprintsEnabled: false,
                resetDeviceEnabled: false,
                onManageFingerprints: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_device_settings_when_device_is_not_found() {
        let view = FormView(
            viewModel: DeviceSettingsFormBodyModelKt.DeviceSettingsFormBodyModel(
                trackerScreenId: SettingsEventTrackerScreenId.settingsDeviceInfoEmpty,
                emptyState: true,
                modelName: "-",
                currentVersion: "-",
                updateVersion: nil,
                modelNumber: "-",
                serialNumber: "-",
                deviceCharge: "-",
                lastSyncDate: "-",
                replacementPending: nil,
                replaceDeviceEnabled: true,
                onUpdateVersion: {},
                onSyncDeviceInfo: {},
                onReplaceDevice: {},
                onManageReplacement: {},
                onResetDevice: {},
                onBack: {},
                multipleFingerprintsEnabled: false,
                resetDeviceEnabled: false,
                onManageFingerprints: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
