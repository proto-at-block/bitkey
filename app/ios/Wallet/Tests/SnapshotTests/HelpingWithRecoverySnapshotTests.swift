import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class HelpingWithRecoverySnapshotTests: XCTestCase {

    func test_verifying_contact_method() {
        let view = FormView(
            viewModel: HelpingWithRecoveryModelsKt.VerifyingContactMethodFormBodyModel(
                onBack: {},
                onTextMessageClick: {},
                onEmailClick: {},
                onPhoneCallClick: {},
                onVideoChatClick: {},
                onInPersonClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_confirming_identity() {
        let view = FormView(
            viewModel: HelpingWithRecoveryModelsKt.ConfirmingIdentityFormBodyModel(
                protectedCustomer: .init(
                    relationshipId: "id",
                    alias: "Customer Name",
                    roles: ["SOCIAL_RECOVERY_CONTACT"]
                ),
                onBack: {},
                onVerifiedClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
