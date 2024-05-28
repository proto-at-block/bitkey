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
            viewModel: AccountAccessMoreOptionsFormBodyModelKt.AccountAccessMoreOptionsFormBodyModel(
                onBack: {},
                onRestoreYourWalletClick: {},
                onBeTrustedContactClick: {}
            )
        )
        assertBitkeySnapshots(view: view)
    }

    func test_connectYourBitkey() {
        let view = PairNewHardwareView(
            viewModel: StartFingerprintEnrollmentInstructionsBodyModelKt.StartFingerprintEnrollmentInstructionsBodyModel(
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
                eventTrackerScreenIdContext: PairHardwareEventTrackerScreenIdContext.hwRecovery
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_onboarding_hw_fingerprint_enrollment() {
        let view = PairNewHardwareView(
            viewModel: HardwareFingerprintEnrollmentScreenModelKt.HardwareFingerprintEnrollmentScreenModel(
                onSaveFingerprint: {},
                onBack: {},
                showingIncompleteEnrollmentError: false,
                incompleteEnrollmentErrorOnPrimaryButtonClick: {},
                onErrorOverlayClosed: {},
                eventTrackerScreenIdContext: PairHardwareEventTrackerScreenIdContext.hwRecovery,
                presentationStyle: .root, 
                isNavigatingBack: false,
                headline: "Set up your fingerprint",
                instructions: "Place your finger on the sensor until you see a blue light." +
                " Repeat this until the device has a solid green light." +
                " Once done, press the button below to save your fingerprint."
            ).body as! PairNewHardwareBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

}
