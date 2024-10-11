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
                headerHeadline: SpendingLimitsCopy.Companion().get(isRevampOn: false)
                    .onboardingModal.headline,
                headerSubline: SpendingLimitsCopy.Companion().get(isRevampOn: false).onboardingModal
                    .subline,
                primaryButtonString: SpendingLimitsCopy.Companion().get(isRevampOn: false)
                    .onboardingModal.primaryButtonString
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_mobile_pay_onboarding_with_revamp() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateMobilePayOnboardingScreenModel(
                onContinue: {},
                onSetUpLater: {},
                onClosed: {},
                headerHeadline: SpendingLimitsCopy.Companion().get(isRevampOn: true).onboardingModal
                    .headline,
                headerSubline: SpendingLimitsCopy.Companion().get(isRevampOn: true).onboardingModal
                    .subline,
                primaryButtonString: SpendingLimitsCopy.Companion().get(isRevampOn: true)
                    .onboardingModal.primaryButtonString
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
