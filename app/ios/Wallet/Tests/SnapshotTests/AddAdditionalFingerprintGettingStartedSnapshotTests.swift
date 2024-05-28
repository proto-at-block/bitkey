import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class AddAdditionalFingerprintGettingStartedSnapshotTests: XCTestCase {
    
    func test_add_additional_fingerprint_getting_started() {
        let view = FormView(
            viewModel: AddAdditionalFingerprintGettingStartedModelKt.AddAdditionalFingerprintGettingStartedModel(
                onClosed: {},
                onContinue: {},
                onSetUpLater: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
