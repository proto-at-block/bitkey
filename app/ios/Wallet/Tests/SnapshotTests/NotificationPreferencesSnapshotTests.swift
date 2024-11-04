import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class NotificationPreferencesSnapshotTests: XCTestCase {

    func test_notifications_preferences_editing() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferenceFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: { _ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: { _ in },
                onUpdatesPushToggle: { _ in },
                onUpdatesEmailToggle: { _ in },
                formEditingState: .editing,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notifications_preferences_editing_tos_agreed() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferenceFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: true,
                    onTermsAgreeToggle: { _ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: { _ in },
                onUpdatesPushToggle: { _ in },
                onUpdatesEmailToggle: { _ in },
                formEditingState: .editing,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notifications_preferences_editing_no_tos() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferenceFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: nil,
                onTransactionPushToggle: { _ in },
                onUpdatesPushToggle: { _ in },
                onUpdatesEmailToggle: { _ in },
                formEditingState: .editing,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notifications_preferences_loading() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferenceFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: { _ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: { _ in },
                onUpdatesPushToggle: { _ in },
                onUpdatesEmailToggle: { _ in },
                formEditingState: .loading,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notifications_preferences_overlay() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferenceFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: { _ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: { _ in },
                onUpdatesPushToggle: { _ in },
                onUpdatesEmailToggle: { _ in },
                formEditingState: .overlay,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notifications_preferences_editing_updatesPush() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferenceFormBodyModel(
                transactionPush: false,
                updatesPush: true,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: { _ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: { _ in },
                onUpdatesPushToggle: { _ in },
                onUpdatesEmailToggle: { _ in },
                formEditingState: .overlay,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_notifications_preferences_showTosWarning() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateNotificationPreferenceFormBodyModel(
                transactionPush: false,
                updatesPush: true,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: { _ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: { _ in },
                onUpdatesPushToggle: { _ in },
                onUpdatesEmailToggle: { _ in },
                formEditingState: .overlay,
                ctaModel: CallToActionModel(
                    text: "Agree to our Terms and Privacy Policy to continue.",
                    treatment: CallToActionModel.Treatment.warning
                ),
                onBack: {},
                continueOnClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
