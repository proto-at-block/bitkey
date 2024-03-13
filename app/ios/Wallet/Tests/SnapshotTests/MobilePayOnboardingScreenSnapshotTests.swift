import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class MobilePayOnboardingScreenSnapshotTests: XCTestCase {
    
    func test_mobile_pay_onboarding() {
        let view = FormView(
            viewModel: MobilePayOnboardingModelKt.MobilePayOnboardingScreenModel(
                onContinue: {},
                onSetUpLater: {},
                onClosed: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
