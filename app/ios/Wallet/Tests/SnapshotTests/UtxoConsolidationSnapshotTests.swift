import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class UtxoConsolidationSnapshotTests: XCTestCase {

    func test_utxo_consolidation_confirmation() {
        let view = FormView(
            viewModel: UtxoConsolidationConfirmationModelKt.utxoConsolidationConfirmationModel(
                balanceFiat: "$15,000",
                balanceBitcoin: "26,259,461 sats",
                utxoCount: "20",
                consolidationCostFiat: "$37.42",
                consolidationCostBitcoin: "65,000 sats",
                onBack: {},
                onConfirmClick: {},
                onConsolidationTimeClick: {},
                onConsolidationCostClick: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_utxo_consolidation_transaction_sent() {
        let view = FormView(
            viewModel: UtxoConsolidationTransactionSentModelKt
                .utxoConsolidationTransactionSentModel(
                    targetAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                    arrivalTime: "Aug 7, 12:24 pm",
                    utxosCountConsolidated: "20 → 1",
                    consolidationCostBitcoin: "65,509 sats",
                    consolidationCostFiat: "$37.42",
                    onBack: {},
                    onDone: {}
                )
        )

        assertBitkeySnapshots(view: view)
    }

}
