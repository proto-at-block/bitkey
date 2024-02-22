import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class TransferConfirmationBodyModelSnapshotTests: XCTestCase {

    func test_transfer_confirmation_require_hw_enabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                requiresHardware: true,
                confirmButtonEnabled: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_not_require_hw_enabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                requiresHardware: false,
                confirmButtonEnabled: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_require_hw_disabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                requiresHardware: true,
                confirmButtonEnabled: false
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_not_require_hw_disabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                requiresHardware: false,
                confirmButtonEnabled: false
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_transfer_confirmation_with_cta_warning() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                requiresHardware: true,
                confirmButtonEnabled: true,
                isSpeedUp: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

}

// MARK: -

private extension FormBodyModel {

    static func transferConfirmationModel(
        requiresHardware: Bool,
        confirmButtonEnabled: Bool,
        isSpeedUp: Bool = false
    ) -> FormBodyModel {
        let displayVariant: TransferConfirmationUiPropsVariant = if (isSpeedUp) {
            TransferConfirmationUiPropsVariantSpeedUp(txid: "abc", oldFee: Fee(amount: BitcoinMoney(fractionalUnitAmount: BignumBigInteger(int: 50_000)), feeRate: FeeRate(satsPerVByte: 1)), newFeeRate: FeeRate(satsPerVByte: 2))
        } else {
            TransferConfirmationUiPropsVariantRegular(selectedPriority: EstimatedTransactionPriority.fastest)
        }
        
        return TransferConfirmationScreenModelKt.TransferConfirmationScreenModel(
            onBack: {},
            onCancel: {},
            variant: displayVariant,
            recipientAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
            transactionDetails: .init(
                transactionDetailModelType: TransactionDetailModelType.Regular(
                    transferAmountText: "$20.00",
                    feeAmountText: "$1.36"
                ),
                transactionSpeedText: "~30 minutes",
                totalAmountPrimaryText: "$21.36",
                totalAmountSecondaryText: "(0.0010 BTC)"
            ),
            requiresHardware: requiresHardware,
            confirmButtonEnabled: confirmButtonEnabled,
            errorOverlayModel: nil,
            onConfirmClick: {},
            onNetworkFeesClick: {},
            onArrivalTimeClick: {}
        ).body as! FormBodyModel
    }

}
