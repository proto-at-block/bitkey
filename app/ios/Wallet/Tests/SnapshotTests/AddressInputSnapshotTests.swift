import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class AddressInputSnapshotTests: XCTestCase {

    func test_address_input_empty_no_paste() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateBitcoinRecipientAddressScreenModel(
                enteredText: "",
                warningText: nil,
                onEnteredTextChanged: { _ in },
                showPasteButton: false,
                onContinueClick: nil,
                onBack: {},
                onScanQrCodeClick: {},
                onPasteButtonClick: {},
                showSelfSendWarningWithRedirect: false,
                onGoToUtxoConsolidation: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_address_input_empty_with_paste() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateBitcoinRecipientAddressScreenModel(
                enteredText: "",
                warningText: nil,
                onEnteredTextChanged: { _ in },
                showPasteButton: true,
                onContinueClick: nil,
                onBack: {},
                onScanQrCodeClick: {},
                onPasteButtonClick: {},
                showSelfSendWarningWithRedirect: false,
                onGoToUtxoConsolidation: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_address_input_empty_nonempty() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateBitcoinRecipientAddressScreenModel(
                enteredText: "tb1qr2vljrk6wyjtvyy5cs35yan8w6xhedrr20a845v8qpg500zutf3sv29dzg",
                warningText: "Some warning text",
                onEnteredTextChanged: { _ in },
                showPasteButton: false,
                onContinueClick: nil,
                onBack: {},
                onScanQrCodeClick: {},
                onPasteButtonClick: {},
                showSelfSendWarningWithRedirect: false,
                onGoToUtxoConsolidation: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_address_input_self_send_warning() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateBitcoinRecipientAddressScreenModel(
                enteredText: "tb1qr2vljrk6wyjtvyy5cs35yan8w6xhedrr20a845v8qpg500zutf3sv29dzg",
                warningText: nil,
                onEnteredTextChanged: { _ in },
                showPasteButton: false,
                onContinueClick: nil,
                onBack: {},
                onScanQrCodeClick: {},
                onPasteButtonClick: {},
                showSelfSendWarningWithRedirect: true,
                onGoToUtxoConsolidation: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
