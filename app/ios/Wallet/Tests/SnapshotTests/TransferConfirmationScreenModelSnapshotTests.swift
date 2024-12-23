import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class TransferConfirmationBodyModelSnapshotTests: XCTestCase {

    func test_transfer_confirmation_require_hw_enabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                displayVariant: TransferConfirmationScreenVariantRegular(),
                requiresHardware: true,
                confirmButtonEnabled: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_not_require_hw_enabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                displayVariant: TransferConfirmationScreenVariantRegular(),
                requiresHardware: false,
                confirmButtonEnabled: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_require_hw_disabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                displayVariant: TransferConfirmationScreenVariantRegular(),
                requiresHardware: true,
                confirmButtonEnabled: false
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_not_require_hw_disabled() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                displayVariant: TransferConfirmationScreenVariantRegular(),
                requiresHardware: false,
                confirmButtonEnabled: false
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_with_cta_warning() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                displayVariant: TransferConfirmationScreenVariantSpeedUp(),
                requiresHardware: true,
                confirmButtonEnabled: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transfer_confirmation_sell() {
        let view = FormView(
            viewModel: .transferConfirmationModel(
                displayVariant: TransferConfirmationScreenVariantSell(
                    partnerInfo: .init(
                        logoUrl: nil,
                        logoBadgedUrl: nil,
                        name: "PartnerX",
                        partnerId: "test-id"
                    )
                ),
                requiresHardware: true,
                confirmButtonEnabled: true
            )
        )

        assertBitkeySnapshots(view: view)
    }

}

// MARK: -

private extension FormBodyModel {

    static func transferConfirmationModel(
        displayVariant: TransferConfirmationScreenVariant,
        requiresHardware: Bool,
        confirmButtonEnabled: Bool
    ) -> FormBodyModel {
        return SnapshotTestModels.shared.CreateTransferConfirmationScreenModel(
            onBack: {},
            variant: displayVariant,
            recipientAddress: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
            transactionDetails: .init(
                transactionDetailModelType: TransactionDetailModelTypeRegular(
                    transferAmountText: "$20.00",
                    transferAmountSecondaryText: "0.0003 BTC",
                    totalAmountPrimaryText: "$21.36",
                    totalAmountSecondaryText: "0.0010 BTC",
                    feeAmountText: "$1.36",
                    feeAmountSecondaryText: "0.00002 BTC"
                ),
                transactionSpeedText: "~30 minutes"
            ),
            requiresHardware: requiresHardware,
            confirmButtonEnabled: confirmButtonEnabled,
            onConfirmClick: {},
            onNetworkFeesClick: {},
            onArrivalTimeClick: {}
        )
    }

}
