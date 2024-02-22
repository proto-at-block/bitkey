import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class MobileTransactionsSnapshotTests: XCTestCase {

    func test_mobile_transactions_on() {
        let view = MobileTransactionsView(
            viewModel: MobilePayStatusModel(
                onBack: {},
                switchIsChecked: true,
                onSwitchCheckedChange: { _ in },
                dailyLimitRow: .init(title: "Daily limit", sideText: "$100.00", onClick: {}),
                disableAlertModel: nil,
                spendingLimitCardModel: .init(
                    titleText: "Today’s limit",
                    dailyResetTimezoneText: "Resets at 3:00am PST",
                    spentAmountText: "$40 spent",
                    remainingAmountText: "$60 remaining",
                    progressPercentage: 0.4
                )
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_mobile_transactions_off() {
        let view = MobileTransactionsView(
            viewModel: MobilePayStatusModel(
                onBack: {},
                switchIsChecked: false,
                onSwitchCheckedChange: { _ in },
                dailyLimitRow: nil,
                disableAlertModel: nil,
                spendingLimitCardModel: nil
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
