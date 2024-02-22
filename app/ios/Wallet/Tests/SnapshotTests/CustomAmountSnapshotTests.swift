import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class CustomAmountSnapshotTests: XCTestCase {
    
    func test_custom_amount_entry_in_range() {
        let view = CustomAmountView(
            viewModel: .init(
                onBack: {},
                limits: "From $10 to $1000",
                amountModel: .init(
                    primaryAmount: "$20.00",
                    primaryAmountGhostedSubstringRange: nil,
                    secondaryAmount: nil,
                    eventTrackerScreenInfo: nil
                ),
                keypadModel: .init(showDecimal: true, onButtonPress: { _ in }),
                continueButtonEnabled: true,
                onNext: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_custom_amount_entry_out_of_range() {
        let view = CustomAmountView(
            viewModel: .init(
                onBack: {},
                limits: "From $10 to $1000",
                amountModel: .init(
                    primaryAmount: "$2.00",
                    primaryAmountGhostedSubstringRange: nil,
                    secondaryAmount: nil,
                    eventTrackerScreenInfo: nil
                ),
                keypadModel: .init(showDecimal: true, onButtonPress: { _ in }),
                continueButtonEnabled: false,
                onNext: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_custom_amount_entry_in_range_ghosted_substring() {
        let view = CustomAmountView(
            viewModel: .init(
                onBack: {},
                limits: "From $10 to $1000",
                amountModel: .init(
                    primaryAmount: "$20.00",
                    primaryAmountGhostedSubstringRange: .init(start: 3, endInclusive: 5),
                    secondaryAmount: nil,
                    eventTrackerScreenInfo: nil
                ),
                keypadModel: .init(showDecimal: true, onButtonPress: { _ in }),
                continueButtonEnabled: true,
                onNext: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
