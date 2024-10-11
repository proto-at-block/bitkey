import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class ViewingAddTrustedContactSnapshotTests: XCTestCase {

    func test_add_trusted_contact() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateViewingAddTrustedContactFormBodyModel(
                onAddTrustedContact: {},
                onSkip: {},
                onClosed: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
