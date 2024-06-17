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
        let transactionDetailModel: TransactionDetailModelType = if isSpeedUp {
            TransactionDetailModelTypeSpeedUp(
                transferAmountText: "$20.00",
                totalAmountPrimaryText: "$22.36",
                totalAmountSecondaryText: "(0.0010 BTC)",
                oldFeeAmountText: "$1.00",
                feeDifferenceText: "+$1.00"
            )
        } else {
            TransactionDetailModelTypeRegular(
                transferAmountText: "$20.00",
                totalAmountPrimaryText: "$21.36",
                totalAmountSecondaryText: "(0.0010 BTC)",
                feeAmountText: "$1.36"
            )
        }

        return TransferInitiatedBodyModelKt.TransferInitiatedBodyModel(
            onBack: {},
            recipientAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
            transactionDetails: TransactionDetailsModel(
                transactionDetailModelType: transactionDetailModel,
                transactionSpeedText: "~30 minutes"
            ),
            onDone: {}
        )
    }
}
