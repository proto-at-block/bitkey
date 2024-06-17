import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class StepperViewSnapshotTests: XCTestCase {

    func test_expected_transaction_notice() {
        let view = FormView(
            viewModel: ExpectedTransactionNoticeModelKt.ExpectedTransactionNoticeModel(
                partnerInfo: nil,
                transactionDate: "Jan 4, 1:00 PM",
                onViewInPartnerApp: { _ in },
                onBack: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
