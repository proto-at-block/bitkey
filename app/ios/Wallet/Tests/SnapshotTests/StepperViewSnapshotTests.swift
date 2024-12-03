import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

// TODO(W-10113): Re-enable snapshot tests)
final class StepperViewSnapshotTests: XCTestCase {

    func skipped_test_expected_transaction_notice() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateExpectedTransactionNoticeModel(
                partnerInfo: nil,
                transactionDate: "Jan 4, 1:00 PM",
                onViewInPartnerApp: { _ in },
                onBack: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
