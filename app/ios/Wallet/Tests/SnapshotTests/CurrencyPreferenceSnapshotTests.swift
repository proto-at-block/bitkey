import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class CurrencyPreferenceSnapshotTests: XCTestCase {

    func test_currency_preference_with_back() {
        let view = FormView(
            viewModel: CurrencyPreferenceFormModelKt.CurrencyPreferenceFormModel(
                onBack: {},
                moneyHomeHero: .init(primaryAmount: "$0", secondaryAmount: "0 sats", isHidden: false),
                fiatCurrencyPreferenceString: "USD",
                onFiatCurrencyPreferenceClick: {},
                bitcoinDisplayPreferenceString: "Sats",
                bitcoinDisplayPreferencePickerModel: .snapshot,
                shouldShowHideBalance: false,
                isHideBalanceEnabled: false,
                onEnableHideBalanceChanged: { _ in },
                onBitcoinDisplayPreferenceClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_currency_preference_with_hide_balance() {
        let view = FormView(
            viewModel: CurrencyPreferenceFormModelKt.CurrencyPreferenceFormModel(
                onBack: {},
                moneyHomeHero: .init(primaryAmount: "$0", secondaryAmount: "0 sats", isHidden: true),
                fiatCurrencyPreferenceString: "USD",
                onFiatCurrencyPreferenceClick: {},
                bitcoinDisplayPreferenceString: "Sats",
                bitcoinDisplayPreferencePickerModel: .snapshot,
                shouldShowHideBalance: true,
                isHideBalanceEnabled: true,
                onEnableHideBalanceChanged: { _ in },
                onBitcoinDisplayPreferenceClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
}

private extension ListItemPickerMenu where Option == AnyObject {
    static let snapshot = ListItemPickerMenuCompanion.shared.invoke(
        isShowing: true,
        selectedOption: "Sats",
        options: ["Sats", "Bitcoin"],
        onOptionSelected: { _ in },
        onDismiss: {}
    ) as! ListItemPickerMenu<AnyObject>
}
