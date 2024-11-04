import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class EmailRecoveryMethodRequiredErrorScreenSnapshotTests: XCTestCase {

    func test_email_receovery_method_required_error_screen() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateEmailRecoveryMethodRequiredErrorModal(
                onCancel: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
