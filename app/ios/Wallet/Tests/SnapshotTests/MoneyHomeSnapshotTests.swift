import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class MoneyHomeSnapshotTests: XCTestCase {

    func test_money_home() {
        let view = MoneyHomeView(viewModel: .snapshotTestFull())
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_with_status_banner() {
        let view = MoneyHomeView(viewModel: .snapshotTestFull())
        assertBitkeySnapshots(view: view, screenModel: .snapshotTest(statusBannerModel: .snapshotTest()))
    }

    func test_money_home_lite() {
        let view = LiteMoneyHomeView(viewModel: .snapshotTestLite(protectedCustomers: ["Alice"]))
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_lite_with_status_banner() {
        let view = LiteMoneyHomeView(viewModel: .snapshotTestLite(protectedCustomers: ["Alice"]))
        assertBitkeySnapshots(view: view, screenModel: .snapshotTest(statusBannerModel: .snapshotTest()))
    }
    
    func test_money_home_lite_with_no_protected_customers() {
        let view = LiteMoneyHomeView(viewModel: .snapshotTestLite(protectedCustomers: []))
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_lite_with_two_protected_customers() {
        let view = LiteMoneyHomeView(viewModel: .snapshotTestLite(protectedCustomers: ["Alice", "Bob"]))
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_with_getting_started_card() {
        let view = MoneyHomeView(viewModel: .snapshotTestFull(cards: [.gettingStarted]))
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_with_device_update_card() {
        let view = MoneyHomeView(viewModel: .snapshotTestFull(cards: [.deviceUpdate]))
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_with_hw_recovery() {
        let view = MoneyHomeView(
            viewModel: .snapshotTestFull(
                cards: [.hardwareStatusReady]
            )
        )
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_with_hw_recovery_and_getting_started() {
        let view = MoneyHomeView(
            viewModel: .snapshotTestFull(
                cards: [.hardwareStatusInProgress, .gettingStarted]
            )
        )
        assertBitkeySnapshots(view: view)
    }

    func test_money_home_with_device_update_and_getting_started() {
        let view = MoneyHomeView(
            viewModel: .snapshotTestFull(cards: [.deviceUpdate, .gettingStarted])
        )
        assertBitkeySnapshots(view: view)
    }
    
    func test_money_home_with_pending_invitation() {
        let view = MoneyHomeView(
            viewModel: .snapshotTestFull(cards: [.pendingInvitation])
        )
        assertBitkeySnapshots(view: view)
    }
    
    func test_money_home_with_expired_invitation() {
        let view = MoneyHomeView(
            viewModel: .snapshotTestFull(cards: [.expiredInvitation])
        )
        assertBitkeySnapshots(view: view)
    }
    
    func test_money_home_with_invitations() {
        let view = MoneyHomeView(
            viewModel: .snapshotTestFull(cards: [.pendingInvitation, .expiredInvitation])
        )
        assertBitkeySnapshots(view: view)
    }
    
    func test_money_home_with_device_update_and_getting_started_and_invitations() {
        let view = MoneyHomeView(
            viewModel: .snapshotTestFull(cards: [.deviceUpdate, .pendingInvitation, .expiredInvitation, .gettingStarted])
        )
        assertBitkeySnapshots(view: view)
    }
    
    func test_money_home_with_hidden_balance() {
        let view = MoneyHomeView(viewModel: .snapshotTestFull(hideBalance: true))
        assertBitkeySnapshots(view: view)
    }
}

// MARK: -
private extension LiteMoneyHomeBodyModel {
    
    static func snapshotTestLite(
        protectedCustomers: [String] = []
    ) -> LiteMoneyHomeBodyModel {
        return LiteMoneyHomeBodyModel(
            onSettings: {},
            buttonModel: MoneyHomeButtonsModelSingleButtonModel(
                onSetUpBitkeyDevice: {}
            ),
            protectedCustomers: protectedCustomers.map {
                ProtectedCustomer(recoveryRelationshipId: "", alias: $0)
            },
            onProtectedCustomerClick: { ProtectedCustomer in },
            onBuyOwnBitkeyClick: {},
            onAcceptInviteClick: {})
    }
}

private extension MoneyHomeBodyModel {

    static func snapshotTestFull(
        cards: [CardModel] = [],
        hideBalance: Bool = false
    ) -> MoneyHomeBodyModel {
        return MoneyHomeBodyModel(
            hideBalance: hideBalance,
            onSettings: {},
            balanceModel: MoneyAmountModel(
                primaryAmount: "$123.75",
                secondaryAmount: "435,228 sats"
            ),
            buttonsModel: MoneyHomeButtonsModelMoneyMovementButtonsModel(
                sendButton: .init(enabled: true, onClick: {}),
                receiveButton: .init(enabled: true, onClick: {}),
                addButton: .init(enabled: false, onClick: {})
            ),
            cardsModel: .init(cards: cards),
            transactionsModel: ListModel.transactionsSnapshotTest,
            seeAllButtonModel: .snapshotTest(text: "See All", treatment: .secondary),
            refresh: TestSuspendFunction(),
            onRefresh: {},
            onHideBalance: {},
            isRefreshing: false
        )
    }
}

// MARK: -

private extension CardModel {

    static let gettingStarted = GettingStartedCardModelKt.GettingStartedCardModel(
        animations: nil,
        taskModels: [
            .init(task: .init(id: .enablespendinglimit, state: .incomplete), isEnabled: false, onClick: {}),
            .init(task: .init(id: .invitetrustedcontact, state: .incomplete), isEnabled: true, onClick: {}),
            .init(task: .init(id: .addbitcoin, state: .incomplete), isEnabled: true, onClick: {}),
            .init(task: .init(id: .addadditionalfingerprint, state: .incomplete), isEnabled: true, onClick: {})
        ]
    )

    static let deviceUpdate = DeviceUpdateCardModelKt.DeviceUpdateCardModel(onUpdateDevice: {})

    static let hardwareStatusInProgress = HardwareRecoveryCardModelKt.HardwareRecoveryCardModel(
        title: "Replacement pending...",
        subtitle: "2 days remaining",
        delayPeriodProgress: 0.75,
        delayPeriodRemainingSeconds: 1000,
        onClick: {}
    )

    static let hardwareStatusReady = HardwareRecoveryCardModelKt.HardwareRecoveryCardModel(
        title: "Replacement Ready",
        subtitle: nil,
        delayPeriodProgress: 1,
        delayPeriodRemainingSeconds: 0,
        onClick: {}
    )
    
    static let pendingInvitation = RecoveryContactCardModelKt.RecoveryContactCardModel(
        contact: Invitation(
            recoveryRelationshipId: "foo",
            trustedContactAlias: "Alice",
            code: "bar",
            codeBitLength: 20,
            expiresAt: Kotlinx_datetimeInstant.companion.DISTANT_FUTURE
        ),
        buttonText: "Pending",
        onClick: {},
        buttonTreatment: .primary
    )
    
    
    static let expiredInvitation = RecoveryContactCardModelKt.RecoveryContactCardModel(
        contact: Invitation(
            recoveryRelationshipId: "foo",
            trustedContactAlias: "Alice",
            code: "bar",
            codeBitLength: 20,
            expiresAt: Kotlinx_datetimeInstant.companion.DISTANT_PAST
        ),
        buttonText: "Expired",
        onClick: {},
        buttonTreatment: .primary
    )

}
