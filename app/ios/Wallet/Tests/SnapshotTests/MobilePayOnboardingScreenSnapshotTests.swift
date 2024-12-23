import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class MobilePayOnboardingScreenSnapshotTests: XCTestCase {

    func test_mobile_pay_onboarding() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateMobilePayOnboardingScreenModel(
                onContinue: {},
                onSetUpLater: {},
                onClosed: {},
                headerHeadline: "Transfer without hardware",
                headerSubline: "Spend up to a set daily limit without your Bitkey device.",
                primaryButtonString: "Got it"
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
