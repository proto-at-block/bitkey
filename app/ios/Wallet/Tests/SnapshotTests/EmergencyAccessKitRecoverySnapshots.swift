import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class EmergencyAccessKitRecoverySnapshots: XCTestCase {

    func test_import_select_method() {
        let view = FormView(
            viewModel: FormScreenModel_EmergencyAccessKitKt.EmergencyAccessKitImportWalletModel(
                onBack: {},
                onScanQRCode: {},
                onEnterManually: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_paste_mobile_key() {
        let view = FormView(
            viewModel: FormScreenModel_EmergencyAccessKitKt.EmergencyAccessKitImportPasteMobileKeyModel(
                enteredText: "",
                onBack: {},
                onEnterTextChanged: {_ in },
                onPasteButtonClick: {},
                onContinue: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_code_not_recognized() {
        let view = FormView(
            viewModel: FormScreenModel_EmergencyAccessKitKt.EmergencyAccessKitCodeNotRecognized(
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
            viewModel: FormScreenModel_EmergencyAccessKitKt.EmergencyAccessKitRestoreWallet(
                onBack: {},
                onRestore: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
