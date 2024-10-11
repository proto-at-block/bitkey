import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class EmergencyAccessKitRecoverySnapshots: XCTestCase {

    func test_import_select_method() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateEmergencyAccessKitImportWalletBodyModel(
                onBack: {},
                onScanQRCode: {},
                onEnterManually: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_paste_mobile_key() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared
                .CreateEmergencyAccessKitImportPasteMobileKeyBodyModel(
                    enteredText: "",
                    onBack: {},
                    onEnterTextChanged: { _ in },
                    onPasteButtonClick: {},
                    onContinue: {}
                )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_code_not_recognized() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateEmergencyAccessKitCodeNotRecognizedBodyModel(
                arrivedFromManualEntry: false,
                onBack: {},
                onScanQRCode: {},
                onImport: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_restore_your_wallet() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateEmergencyAccessKitRestoreWalletBodyModel(
                onBack: {},
                onRestore: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
