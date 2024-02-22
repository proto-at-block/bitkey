import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class FwupSnapshotTests: XCTestCase {

    func test_update_device() {
        let view = FwupInstructionsView(
            viewModel: FwupUpdateDeviceModelKt.FwupUpdateDeviceModel(
                onLaunchFwup: {},
                onClose: {},
                bottomSheetModel: nil
            ).body as! FwupInstructionsBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_nfc_mask() {
        let view = NfcMaskView(
            viewModel: NfcMaskView.ViewModel(
                title: "Updating...",
                subtitle: "Continue holding to phone"
            )
        )
        assertBitkeySnapshots(view: view, usesVisualEffect: true, precision: 0.98)
    }

}

