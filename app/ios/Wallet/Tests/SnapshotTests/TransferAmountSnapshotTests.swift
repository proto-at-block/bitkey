import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class TransferAmountSnapshotTests: XCTestCase {

    func test_transfer_amount_entry_fiat() {
        let view = TransferAmountView(
            viewModel: .init(
                onBack: {},
                balanceTitle: "$961.24 available",
                amountModel: .init(
                    primaryAmount: "$0.00",
                    primaryAmountGhostedSubstringRange: .init(start: 3, endInclusive: 4),
                    secondaryAmount: "0 sats",
                    eventTrackerScreenInfo: nil
                ),
                keypadModel: .init(showDecimal: true, onButtonPress: { _ in }),
                cardModel: CardModel(
                    heroImage: nil,
                    title: Shared.LabelModelStringWithStyledSubstringModel.companion.from(
                        string: "Bitkey approval required",
                        boldedSubstrings: []
                    ),
                    subtitle: nil,
                    leadingImage: CardModelCardImageStaticImage(
                        icon: .smalliconbitkey,
                        iconTint: nil
                    ),
                    trailingButton: nil,
                    content: nil,
                    style: CardModel.CardStyleOutline(),
                    onClick: nil,
                    animation: nil
                ),
                continueButtonEnabled: false,
                amountDisabled: false,
                onContinueClick: {},
                onSwapCurrencyClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_amount_entry_fiat_amount_above() {
        let view = TransferAmountView(
            viewModel: .init(
                onBack: {},
                balanceTitle: "$961.24 available",
                amountModel: .init(
                    primaryAmount: "$1,234.00",
                    primaryAmountGhostedSubstringRange: .init(start: 7, endInclusive: 8),
                    secondaryAmount: "12,456 sats",
                    eventTrackerScreenInfo: nil
                ),
                keypadModel: .init(showDecimal: true, onButtonPress: { _ in }),
                cardModel: CardModel(
                    heroImage: nil,
                    title: Shared.LabelModelStringWithStyledSubstringModel.companion.from(
                        string: "Send Max (balance minus fees)",
                        substringToColor: [
                            "(balance minus fees)": .on60,
                        ]
                    ),
                    subtitle: nil,
                    leadingImage: nil,
                    trailingButton: nil,
                    content: nil,
                    style: CardModel.CardStyleOutline(),
                    onClick: nil,
                    animation: nil
                ),
                continueButtonEnabled: false,
                amountDisabled: false,
                onContinueClick: {},
                onSwapCurrencyClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_amount_entry_sats() {
        let view = TransferAmountView(
            viewModel: .init(
                onBack: {},
                balanceTitle: "$961.24 available",
                amountModel: .init(
                    primaryAmount: "123 sats",
                    primaryAmountGhostedSubstringRange: nil,
                    secondaryAmount: "$0.00",
                    eventTrackerScreenInfo: nil
                ),
                keypadModel: .init(showDecimal: false, onButtonPress: { _ in }),
                cardModel: nil,
                continueButtonEnabled: true,
                amountDisabled: false,
                onContinueClick: {},
                onSwapCurrencyClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
