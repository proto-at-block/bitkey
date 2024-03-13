import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class NotificationPreferencesSnapshotTests: XCTestCase {
    
    func test_notifications_preferences_editing() {
        let view = FormView(
            viewModel:
                NotificationPreferencesFormBodyModelKt.NotificationPreferencesFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: {_ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: {_ in },
                onUpdatesPushToggle: {_ in },
                onUpdatesEmailToggle: {_ in },
                formEditingState: .editing,
                alertModel: nil,
                networkingErrorState: nil,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            ).body as! FormBodyModel
        )
        
        assertBitkeySnapshots(view: view)
    }
    
    func test_notifications_preferences_editing_tos_agreed() {
        let view = FormView(
            viewModel:
                NotificationPreferencesFormBodyModelKt.NotificationPreferencesFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: true,
                    onTermsAgreeToggle: {_ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: {_ in },
                onUpdatesPushToggle: {_ in },
                onUpdatesEmailToggle: {_ in },
                formEditingState: .editing,
                alertModel: nil,
                networkingErrorState: nil,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            ).body as! FormBodyModel
        )
        
        assertBitkeySnapshots(view: view)
    }
    
    func test_notifications_preferences_editing_no_tos() {
        let view = FormView(
            viewModel:
                NotificationPreferencesFormBodyModelKt.NotificationPreferencesFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: nil,
                onTransactionPushToggle: {_ in },
                onUpdatesPushToggle: {_ in },
                onUpdatesEmailToggle: {_ in },
                formEditingState: .editing,
                alertModel: nil,
                networkingErrorState: nil,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            ).body as! FormBodyModel
        )
        
        assertBitkeySnapshots(view: view)
    }
    
    func test_notifications_preferences_loading() {
        let view = FormView(
            viewModel:
                NotificationPreferencesFormBodyModelKt.NotificationPreferencesFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: {_ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: {_ in },
                onUpdatesPushToggle: {_ in },
                onUpdatesEmailToggle: {_ in },
                formEditingState: .loading,
                alertModel: nil,
                networkingErrorState: nil,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            ).body as! FormBodyModel
        )
        
        assertBitkeySnapshots(view: view)
    }
    
    func test_notifications_preferences_overlay() {
        let view = FormView(
            viewModel:
                NotificationPreferencesFormBodyModelKt.NotificationPreferencesFormBodyModel(
                transactionPush: false,
                updatesPush: false,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: {_ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: {_ in },
                onUpdatesPushToggle: {_ in },
                onUpdatesEmailToggle: {_ in },
                formEditingState: .overlay,
                alertModel: nil,
                networkingErrorState: nil,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            ).body as! FormBodyModel
        )
        
        assertBitkeySnapshots(view: view)
    }
    
    func test_notifications_preferences_editing_updatesPush() {
        let view = FormView(
            viewModel:
                NotificationPreferencesFormBodyModelKt.NotificationPreferencesFormBodyModel(
                transactionPush: false,
                updatesPush: true,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: {_ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: {_ in },
                onUpdatesPushToggle: {_ in },
                onUpdatesEmailToggle: {_ in },
                formEditingState: .overlay,
                alertModel: nil,
                networkingErrorState: nil,
                ctaModel: nil,
                onBack: {},
                continueOnClick: {}
            ).body as! FormBodyModel
        )
        
        assertBitkeySnapshots(view: view)
    }
    
    func test_notifications_preferences_showTosWarning() {
        let view = FormView(
            viewModel:
                NotificationPreferencesFormBodyModelKt.NotificationPreferencesFormBodyModel(
                transactionPush: false,
                updatesPush: true,
                updatesEmail: false,
                tosInfo: TosInfo(
                    termsAgree: false,
                    onTermsAgreeToggle: {_ in },
                    tosLink: {},
                    privacyLink: {}
                ),
                onTransactionPushToggle: {_ in },
                onUpdatesPushToggle: {_ in },
                onUpdatesEmailToggle: {_ in },
                formEditingState: .overlay,
                alertModel: nil,
                networkingErrorState: nil,
                ctaModel: CallToActionModel(
                    text: "Agree to our Terms and Privacy Policy to continue.",
                    treatment: CallToActionModel.Treatment.warning
                ),
                onBack: {},
                continueOnClick: {}
            ).body as! FormBodyModel
        )
        
        assertBitkeySnapshots(view: view)
    }
}
