import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class AppearancePreferenceSnapshotTests: XCTestCase {

    func test_appearance_preference_with_back() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateAppearancePreferenceFormModel(
                onBack: {},
                moneyHomeHero: .init(
                    primaryAmount: "$0",
                    secondaryAmount: "0 sats",
                    isHidden: false
                ),
                fiatCurrencyPreferenceString: "USD",
                onFiatCurrencyPreferenceClick: {},
                bitcoinDisplayPreferenceString: "Sats",
                bitcoinDisplayPreferencePickerModel: .snapshot,
                isBitcoinPriceCardEnabled: false,
                isHideBalanceEnabled: false,
                onEnableHideBalanceChanged: { _ in },
                onBitcoinDisplayPreferenceClick: {},
                onBitcoinPriceCardPreferenceClick: { _ in }
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
