import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class SuccessSnapshotTests: XCTestCase {

    func test_success_explicit() {
        let view = SuccessView(
            viewModel: .init(
                title: "You have succeeded",
                message: nil,
                style: SuccessBodyModelStyleExplicit(onPrimaryButtonClick: {}),
                id: .none
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_success_explicit_with_message() {
        let view = SuccessView(
            viewModel: .init(
                title: "You have succeeded",
                message: "Congratulations for doing such a great job.",
                style: SuccessBodyModelStyleExplicit(onPrimaryButtonClick: {}),
                id: .none
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_success_implicit() {
        let view = SuccessView(
            viewModel: .init(
                title: "You have succeeded",
                message: nil,
                style: SuccessBodyModelStyleImplicit(),
                id: .none
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
