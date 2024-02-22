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
                bannerModel: .HardwareRequiredBannerModel(),
                continueButtonEnabled: false,
                amountDisabled: false,
                onSendMaxClick: {},
                onContinueClick: {},
                onSwapCurrencyClick: {}, 
                onHardwareRequiredClick: {}
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
                bannerModel: .AmountEqualOrAboveBalanceBannerModel(),
                continueButtonEnabled: false,
                amountDisabled: false,
                onSendMaxClick: {},
                onContinueClick: {},
                onSwapCurrencyClick: {}, 
                onHardwareRequiredClick: {}
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
                bannerModel: nil,
                continueButtonEnabled: true,
                amountDisabled: false,
                onSendMaxClick: {},
                onContinueClick: {},
                onSwapCurrencyClick: {}, 
                onHardwareRequiredClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
