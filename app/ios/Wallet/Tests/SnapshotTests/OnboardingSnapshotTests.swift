import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class OnboardingSnapshotTests: XCTestCase {

    func test_choose_account_access() {
        let view = ChooseAccountAccessView(
            viewModel: .init(
                onLogoClick: {},
                onSetUpNewWalletClick: {},
                onMoreOptionsClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_accountAccessMoreOptions() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateAccountAccessMoreOptionsFormBodyModel(
                onBack: {},
                onRestoreYourWalletClick: {},
                onBeTrustedContactClick: {},
                onResetExistingDevice: {}
            )
        )
        assertBitkeySnapshots(view: view)
    }

    func test_connectYourBitkey() {
        let view = PairNewHardwareView(
            viewModel: StartFingerprintEnrollmentInstructionsBodyModelKt
                .StartFingerprintEnrollmentInstructionsBodyModel(
                    onButtonClick: {},
                    onBack: {},
                    eventTrackerScreenIdContext: PairHardwareEventTrackerScreenIdContext.hwRecovery,

                    isNavigatingBack: false
                )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_onboarding_activation_instructions() {
        let view = PairNewHardwareView(
            viewModel: ActivationInstructionsBodyModelKt.ActivationInstructionsBodyModel(
                onContinue: {},
                onBack: {},
                isNavigatingBack: false,
                eventTrackerContext: PairHardwareEventTrackerScreenIdContext.hwRecovery
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_onboarding_hw_fingerprint_enrollment() {
        let view = PairNewHardwareView(
            viewModel: HardwareFingerprintEnrollmentScreenModelKt
                .HardwareFingerprintEnrollmentScreenModel(
                    onSaveFingerprint: {},
                    onBack: {},
                    showingIncompleteEnrollmentError: false,
                    incompleteEnrollmentErrorOnPrimaryButtonClick: {},
                    onErrorOverlayClosed: {},
                    eventTrackerContext: PairHardwareEventTrackerScreenIdContext.hwRecovery,
                    presentationStyle: .root,

                    isNavigatingBack: false,
                    headline: "Set up your first fingerprint",
                    instructions: "Place your finger on the sensor until you see a blue light. Lift your finger and" +
                        " repeat (15-20 times) adjusting your finger position slightly each time, until the light turns" +
                        " green. Then save your fingerprint."
                ).body as! PairNewHardwareBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

}
