import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class NotificationSnapshotTests: XCTestCase {

    func test_notification_preferences_setup_all_needs_action() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferencesSetupFormBodyModel(
                pushItem: .init(state: .needsaction, onClick: {}),
                smsItem: .init(state: .needsaction, onClick: {}),
                emailItem: .init(state: .needsaction, onClick: {})
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notification_preferences_setup_all_skipped() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferencesSetupFormBodyModel(
                pushItem: .init(state: .skipped, onClick: {}),
                smsItem: .init(state: .skipped, onClick: {}),
                emailItem: .init(state: .skipped, onClick: {})
            )
        )
        assertBitkeySnapshots(view: view)
    }

    func test_notification_preferences_setup_all_needs_action_v2() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferencesSetupFormBodyModel(
                pushItem: .init(state: .needsaction, onClick: {}),
                smsItem: .init(state: .needsaction, onClick: {}),
                emailItem: .init(state: .needsaction, onClick: {})
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_recovery_channel_setup_all_needs_action() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateRecoveryChannelsSetupFormBodyModel(
                pushItem: .init(
                    state: .notcompleted,
                    displayValue: "Recommended",
                    uiErrorHint: .none,
                    onClick: {}
                ),
                smsItem: .init(
                    state: .notcompleted,
                    displayValue: "Recommended",
                    uiErrorHint: .none,
                    onClick: {}
                ),
                emailItem: .init(
                    state: .notcompleted,
                    displayValue: "Required",
                    uiErrorHint: .none,
                    onClick: {}
                ),
                onBack: {},
                learnOnClick: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notification_preferences_setup_all_completed() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferencesSetupFormBodyModel(
                pushItem: .init(state: .completed, onClick: {}),
                smsItem: .init(state: .completed, onClick: {}),
                emailItem: .init(state: .completed, onClick: {})
            )
        )
        assertBitkeySnapshots(view: view)
    }

    func test_notification_preferences_setup_all_completed_v2() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateRecoveryChannelsSetupFormBodyModel(
                pushItem: .init(
                    state: .completed,
                    displayValue: nil,
                    uiErrorHint: .none,
                    onClick: {}
                ),
                smsItem: .init(
                    state: .completed,
                    displayValue: "555-123-4567",
                    uiErrorHint: .none,
                    onClick: {}
                ),
                emailItem: .init(
                    state: .completed,
                    displayValue: "hello@example.com",
                    uiErrorHint: .none,
                    onClick: {}
                ),
                onBack: {},
                learnOnClick: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_phone_input_empty() {
        let view = FormView(
            viewModel: PhoneNumberInputScreenModelKt.PhoneNumberInputScreenModel(
                title: "Enter your phone number",
                subline: nil,
                textFieldValue: "",
                textFieldPlaceholder: "+1 (555) 555-5555",
                textFieldSelection: .init(start: 0, endInclusive: 0),
                onTextFieldValueChange: { _, _ in },
                primaryButton: .snapshotTest(text: "Continue"),
                onClose: {},
                onSkip: {},
                errorOverlayModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_phone_input_empty_v2() {
        let view = FormView(
            viewModel: PhoneNumberInputScreenModelKt.PhoneNumberInputScreenModel(
                title: "Enter your phone number",
                subline: "Recommended",
                textFieldValue: "",
                textFieldPlaceholder: "+1 (555) 555-5555",
                textFieldSelection: .init(start: 0, endInclusive: 0),
                onTextFieldValueChange: { _, _ in },
                primaryButton: .snapshotTest(text: "Continue"),
                onClose: {},
                onSkip: {},
                errorOverlayModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_phone_input_nonempty() {
        let view = FormView(
            viewModel: PhoneNumberInputScreenModelKt.PhoneNumberInputScreenModel(
                title: "Enter your phone number",
                subline: nil,
                textFieldValue: "+1 (555) 555-5555",
                textFieldPlaceholder: "+1 (555) 555-5555",
                textFieldSelection: .init(start: 0, endInclusive: 0),
                onTextFieldValueChange: { _, _ in },
                primaryButton: .snapshotTest(text: "Continue"),
                onClose: {},
                onSkip: nil,
                errorOverlayModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_phone_input_nonempty_v2() {
        let view = FormView(
            viewModel: PhoneNumberInputScreenModelKt.PhoneNumberInputScreenModel(
                title: "Enter your phone number",
                subline: "Recommended",
                textFieldValue: "+1 (555) 555-5555",
                textFieldPlaceholder: "+1 (555) 555-5555",
                textFieldSelection: .init(start: 0, endInclusive: 0),
                onTextFieldValueChange: { _, _ in },
                primaryButton: .snapshotTest(text: "Continue"),
                onClose: {},
                onSkip: nil,
                errorOverlayModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_verify_code_empty() {
        let view = FormView(
            viewModel: VerificationCodeInputBodyModelKt.VerificationCodeInputBodyModel(
                title: "Verify some touchpoint",
                subtitle: "We sent a code to you",
                value: "",
                resendCodeContent: FormMainContentModelVerificationCodeInputResendCodeContentText(
                    value: "Resend code in 00:25"
                ),
                skipForNowContent: FormMainContentModelVerificationCodeInputSkipForNowContentShowing(
                    text: "Can’t receive the code?",
                    onSkipForNow: {}
                ),
                explainerText: nil,
                errorOverlay: nil,
                onValueChange: { _ in },
                onBack: {},
                id: .none
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_verify_code_with_text() {
        let view = FormView(
            viewModel: VerificationCodeInputBodyModelKt.VerificationCodeInputBodyModel(
                title: "Verify some touchpoint",
                subtitle: "We sent a code to you",
                value: "12345",
                resendCodeContent: FormMainContentModelVerificationCodeInputResendCodeContentButton(
                    onSendCodeAgain: {}, isLoading: false
                ),
                skipForNowContent: FormMainContentModelVerificationCodeInputSkipForNowContentHidden(
                ),
                explainerText: nil,
                errorOverlay: nil,
                onValueChange: { _ in },
                onBack: {},
                id: .none
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_verify_code_with_resend_loading() {
        let view = FormView(
            viewModel: VerificationCodeInputBodyModelKt.VerificationCodeInputBodyModel(
                title: "Verify some touchpoint",
                subtitle: "We sent a code to you",
                value: "12345",
                resendCodeContent: FormMainContentModelVerificationCodeInputResendCodeContentButton(
                    onSendCodeAgain: {}, isLoading: true
                ),
                skipForNowContent: FormMainContentModelVerificationCodeInputSkipForNowContentHidden(
                ),
                explainerText: nil,
                errorOverlay: nil,
                onValueChange: { _ in },
                onBack: {},
                id: .none
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_verify_code_empty_with_explainer() {
        let view = FormView(
            viewModel: VerificationCodeInputBodyModelKt.VerificationCodeInputBodyModel(
                title: "Verify some touchpoint",
                subtitle: "We sent a code to you",
                value: "",
                resendCodeContent: FormMainContentModelVerificationCodeInputResendCodeContentText(
                    value: "Resend code in 00:25"
                ),
                skipForNowContent: FormMainContentModelVerificationCodeInputSkipForNowContentShowing(
                    text: "Can’t receive the code?",
                    onSkipForNow: {}
                ),
                explainerText: "If the code doesn’t arrive, please check your spam folder.",
                errorOverlay: nil,
                onValueChange: { _ in },
                onBack: {},
                id: .none
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

}
