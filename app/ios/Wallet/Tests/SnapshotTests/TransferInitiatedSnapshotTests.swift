import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class TransferInitiatedScreenModelSnapshotTests: XCTestCase {

    func test_transfer_initiated() {
        let view = FormView(
            viewModel: .transferInitiatedModel(isSpeedUp: false)
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_transfer_initiated_speedup() {
        let view = FormView(
            viewModel: .transferInitiatedModel(isSpeedUp: true)
        )

        assertBitkeySnapshots(view: view)
    }
}

// MARK: -

private extension FormBodyModel {
    static func transferInitiatedModel(
        isSpeedUp: Bool
    ) -> FormBodyModel {
        let transactionDetailModel = if (isSpeedUp) {
            TransactionDetailModelType.SpeedUp(
                transferAmountText: "$20.00",
                oldFeeAmountText: "$1.00",
                feeDifferenceText: "+$1.00"
            )
        } else {
            TransactionDetailModelType.Regular(
                transferAmountText: "$20.00",
                feeAmountText: "$1.36"
            )
        }
        
        return TransferInitiatedBodyModelKt.TransferInitiatedBodyModel(
            onBack: {},
            recipientAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
            transactionDetails: .init(
                transactionDetailModelType: transactionDetailModel,
                transactionSpeedText: "~30 minutes",
                totalAmountPrimaryText: "$21.36",
                totalAmountSecondaryText: "(0.0010 BTC)"
            ),
            onDone: {}
        )
    }
}
