import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class TrustedContactsListSnapshotTests: XCTestCase {

    func test_trusted_contacts_list() {
        let view = FormView(
            viewModel: TrustedContactsListBodyModelKt.TrustedContactsListBodyModel(
                contacts: [.init(recoveryRelationshipId: "", trustedContactAlias: "Bob", identityKey: .init(key: AppKeyCompanion().fromPublicKey(value: ""))) ],
                invitations: [.init(recoveryRelationshipId: "", trustedContactAlias: "Alice", token: "", expiresAt: .companion.DISTANT_FUTURE)],
                protectedCustomers: [],
                now: Int64(Date().timeIntervalSince1970),
                onAddPressed: {},
                onContactPressed: { _ in },
                onProtectedCustomerPressed: { _ in },
                onAcceptInvitePressed: {},
                onBackPressed: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_trusted_contacts_list_lite() {
        let view = FormView(
            viewModel: LiteTrustedContactsListBodyModelKt.LiteTrustedContactsListBodyModel(
                id: .tcManagementSettingsListLite,
                protectedCustomers: [.init(recoveryRelationshipId: "", alias: "Alice")],
                onProtectedCustomerPressed: { _ in  },
                onAcceptInvitePressed: {},
                onBackPressed: {},
                subline: LiteTrustedContactsListBodyModelKt.LITE_TRUSTED_CONTACTS_LIST_HEADER_SUBLINE
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
