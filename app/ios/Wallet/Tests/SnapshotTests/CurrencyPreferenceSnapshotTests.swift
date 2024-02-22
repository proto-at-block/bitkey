import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class CurrencyPreferenceSnapshotTests: XCTestCase {

    func test_currency_preference_without_back() {
        let view = FormView(
            viewModel: CurrencyPreferenceFormModelKt.CurrencyPreferenceFormModel(
                onBack: nil,
                moneyHomeHero: .init(primaryAmount: "$0", secondaryAmount: "0 sats"),
                fiatCurrencyPreferenceString: "USD",
                onFiatCurrencyPreferenceClick: {},
                bitcoinDisplayPreferenceString: "Sats",
                bitcoinDisplayPreferencePickerModel: .snapshot,
                onBitcoinDisplayPreferenceClick: {},
                onDone: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_currency_preference_with_back() {
        let view = FormView(
            viewModel: CurrencyPreferenceFormModelKt.CurrencyPreferenceFormModel(
                onBack: {},
                moneyHomeHero: .init(primaryAmount: "$0", secondaryAmount: "0 sats"),
                fiatCurrencyPreferenceString: "USD",
                onFiatCurrencyPreferenceClick: {},
                bitcoinDisplayPreferenceString: "Sats",
                bitcoinDisplayPreferencePickerModel: .snapshot,
                onBitcoinDisplayPreferenceClick: {},
                onDone: {}
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
