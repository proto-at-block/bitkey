import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class UtxoConsolidationSnapshotTests: XCTestCase {

    func test_utxo_consolidation_confirmation_without_unconfirmed_transactions_callout() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateUtxoConsolidationConfirmationModel(
                balanceTitle: "Wallet balance",
                balanceAmountDisplayText: .init(
                    primaryAmountText: "$15,000",
                    secondaryAmountText: "26,259,461 sats"
                ),
                utxoCount: "20",
                consolidationCostDisplayText: .init(
                    primaryAmountText: "$37.42",
                    secondaryAmountText: "65,000 sats"
                ),
                estimatedConsolidationTime: "~24 hours",
                showUnconfirmedTransactionsCallout: false,
                onBack: {},
                onContinue: {},
                onConsolidationTimeClick: {},
                onConsolidationCostClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_utxo_consolidation_confirmation_with_unconfirmed_transactions_callout() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateUtxoConsolidationConfirmationModel(
                balanceTitle: "Wallet balance",
                balanceAmountDisplayText: .init(
                    primaryAmountText: "$15,000",
                    secondaryAmountText: "26,259,461 sats"
                ),
                utxoCount: "20",
                consolidationCostDisplayText: .init(
                    primaryAmountText: "$37.42",
                    secondaryAmountText: "65,000 sats"
                ),
                estimatedConsolidationTime: "~24 hours",
                showUnconfirmedTransactionsCallout: true,
                onBack: {},
                onContinue: {},
                onConsolidationTimeClick: {},
                onConsolidationCostClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_utxo_consolidation_transaction_sent() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateUtxoConsolidationTransactionSentModel(
                targetAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                arrivalTime: "Aug 7, 12:24 pm",
                utxosCountConsolidated: "20 → 1",
                consolidationCostDisplayText: .init(
                    primaryAmountText: "$37.42",
                    secondaryAmountText: "65,509 sats"
                ),
                onBack: {},
                onDone: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_utxo_consolidation_speed_up_confirmation() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateUtxoConsolidationSpeedUpConfirmationModel(
                onBack: {},
                onCancel: {},
                recipientAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                transactionSpeedText: "~10 minutes",
                originalConsolidationCost: "$37.42",
                originalConsolidationCostSecondaryText: "58,761 sats",
                consolidationCostDifference: "+$2.48",
                consolidationCostDifferenceSecondaryText: "3,849 sats",
                totalConsolidationCost: "$39.90",
                totalConsolidationCostSecondaryText: "62,610 sats",
                onConfirmClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_utxo_consolidation_speed_up_transaction_sent() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateUtxoConsolidationSpeedUpTransactionSentModel(
                targetAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                arrivalTime: "~10 minutes",
                originalConsolidationCost: "$37.42",
                originalConsolidationCostSecondaryText: "58,761 sats",
                consolidationCostDifference: "+$2.48",
                consolidationCostDifferenceSecondaryText: "3,849 sats",
                totalConsolidationCost: "$39.90",
                totalConsolidationCostSecondaryText: "62,610 sats",
                onBack: {},
                onDone: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_exceeds_max_utxo_count() {
        let view = FormView(
            viewModel: ExceedsMaxUtxoCountBodyModelKt.ExceedsMaxUtxoCountBodyModel(
                onBack: {},
                maxUtxoCount: 50,
                onContinue: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_utxo_tap_and_hold_to_consolidate() {
        let view = FormView(
            viewModel: SnapshotTestModels.shared.CreateTapAndHoldToConsolidateUtxosBodyModel(
                onBack: {},
                onConsolidate: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
