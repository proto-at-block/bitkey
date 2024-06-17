import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class SuccessSnapshotTests: XCTestCase {

    func test_success_explicit() {
        let view = FormView(
            viewModel: SuccessBodyModelKt.SuccessBodyModel(
                title: "You have succeeded",
                message: nil,
                primaryButtonModel: .init(
                    text: "Done",
                    isLoading: false,
                    onClick: {},
                    leadingIcon: nil
                ),
                id: .none
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_success_explicit_with_message() {
        let view = FormView(
            viewModel: SuccessBodyModelKt.SuccessBodyModel(
                title: "You have succeeded",
                message: "Congratulations for doing such a great job.",
                primaryButtonModel: .init(
                    text: "Done",
                    isLoading: false,
                    onClick: {},
                    leadingIcon: nil
                ),
                id: .none
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_success_implicit() {
        let view = FormView(
            viewModel: SuccessBodyModelKt.SuccessBodyModel(
                title: "You have succeeded",
                message: nil,
                primaryButtonModel: nil,
                id: .none
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
