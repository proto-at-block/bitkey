import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class RecoveryConflictSnapshotTests: XCTestCase {

    func test_someone_else_hw() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateShowingSomeoneElseIsRecoveringBodyModel(
                cancelingRecoveryLostFactor: .hardware,
                isLoading: false,
                onCancelRecovery: {}
            )
        )

        assertBitkeySnapshots(view: view, precision: 0.99)
    }

    func test_someone_else_app() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateShowingSomeoneElseIsRecoveringBodyModel(
                cancelingRecoveryLostFactor: .app,
                isLoading: false,
                onCancelRecovery: {}
            )
        )

        assertBitkeySnapshots(view: view, precision: 0.99)
    }

    func test_no_longer_hw() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateShowingNoLongerRecoveringBodyModel(
                canceledRecoveringFactor: .hardware,
                isLoading: false,
                errorData: nil,
                onAcknowledge: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_no_longer_app() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateShowingNoLongerRecoveringBodyModel(
                canceledRecoveringFactor: .app,
                isLoading: false,
                errorData: nil,
                onAcknowledge: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
