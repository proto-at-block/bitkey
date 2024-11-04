import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class TransactionDetailSnapshotTests: XCTestCase {

    func test_transaction_detail_sent() {
        let view = FormView(
            viewModel: .transactionDetailModel(
                isPending: false,
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: false,
                content: [
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Confirmed at",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "03-17-1963",
                                secondarySideText: nil,
                                showBottomDivider: false
                            ),
                        ],
                        total: nil,
                        buttons: []
                    ),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Recipient received",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$9.00",
                                secondarySideText: "35,584 sats",
                                showBottomDivider: false
                            ),
                            .init(
                                withTitle: "Network fees",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$1.00",
                                secondarySideText: "5,526 sats",
                                showBottomDivider: false
                            ),
                        ],
                        total: .init(
                            withTitle: "Total",
                            secondaryTitle: "At time sent",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "$10.00",
                            sideTextType: .body2bold,
                            secondarySideText: "41,110 sats",
                            showBottomDivider: false
                        ),
                        buttons: []
                    ),
                ]
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transaction_detail_received() {
        let view = FormView(
            viewModel: .transactionDetailModel(
                isPending: false,
                transactionType: BitcoinTransactionTransactionTypeIncoming(),
                isFeeBumpEnabled: false,
                content: [
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [.init(
                            withTitle: "Confirmed at",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "03-17-1963",
                            secondarySideText: nil,
                            showBottomDivider: false
                        )],
                        total: nil,
                        buttons: []
                    ),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [],
                        total: .init(
                            withTitle: "Amount received",
                            secondaryTitle: "At time confirmed",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "$10.00",
                            sideTextType: .body2bold,
                            secondarySideText: "35,584 sats",
                            showBottomDivider: false
                        ),
                        buttons: []
                    ),
                ]
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transaction_detail_utxoConsolidation() {
        let view = FormView(
            viewModel: .transactionDetailModel(
                isPending: false,
                transactionType: BitcoinTransactionTransactionTypeUtxoConsolidation(),
                isFeeBumpEnabled: false,
                content: [
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [.init(
                            withTitle: "Confirmed at",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "Sep 20 at 1:28 pm",
                            secondarySideText: nil,
                            showBottomDivider: false
                        )],
                        total: nil,
                        buttons: []
                    ),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "UTXOs consolidated",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "2 → 1",
                                secondarySideText: nil,
                                showBottomDivider: false
                            ),
                            .init(
                                withTitle: "Consolidation cost",
                                secondaryTitle: "At time confirmed",
                                titleTextType: .bold,
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$3.07",
                                secondarySideText: "2000 sats",
                                showBottomDivider: false
                            ),
                        ],
                        total: nil,
                        buttons: []
                    ),
                ]
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transaction_detail_pending() {
        let view = FormView(
            viewModel: .transactionDetailModel(
                isPending: true,
                isDelayed: false,
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: false,
                content: [
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [],
                        total: .init(
                            withTitle: "Amount receiving",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "~$201.36",
                            sideTextType: .body2bold,
                            secondarySideText: "1,312,150 sats",
                            showBottomDivider: false
                        ),
                        buttons: []
                    ),
                ]
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_transaction_detail_pending_late() {
        let view = FormView(
            viewModel: .transactionDetailModel(
                isPending: true,
                isDelayed: true,
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: true,
                content: [
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Should have arrived by",
                                titleIcon: nil,
                                onTitle: nil,
                                sideText: "Aug 7, 12:14pm",
                                sideTextType: .regular,
                                sideTextTreatment: .strikethrough,
                                secondarySideText: "7m late",
                                secondarySideTextType: .bold,
                                secondarySideTextTreatment: .warning,
                                explainer: FormMainContentModel.DataListDataExplainer(
                                    title: "Taking longer than usual",
                                    subtitle: "You can either wait for this transaction to be confirmed or speed it up – you'll need to pay a higher network fee.",
                                    iconButton: IconButtonModel(
                                        iconModel: IconModel(
                                            iconImage: .LocalImage(
                                                icon: .smalliconinformationfilled
                                            ),
                                            iconSize: .xsmall,
                                            iconBackgroundType: IconBackgroundTypeCircle(
                                                circleSize: .xsmall,
                                                color: .translucentblack
                                            ),
                                            iconTint: nil,
                                            iconOpacity: 0.20,
                                            iconTopSpacing: nil,
                                            text: nil
                                        ),
                                        onClick: StandardClick {},
                                        enabled: true
                                    )
                                )
                            ),
                        ],
                        total: nil,
                        buttons: []
                    ),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Recipient receives",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$200.00",
                                secondarySideText: "328,666 sats",
                                showBottomDivider: false
                            ),
                            .init(
                                withTitle: "Network fees",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$1.36",
                                secondarySideText: "2,235 sats",
                                showBottomDivider: false
                            ),
                        ],
                        total: .init(
                            withTitle: "Total",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "~$201.36",
                            sideTextType: .body2bold,
                            secondarySideText: "330,901 sats",
                            showBottomDivider: false
                        ),
                        buttons: []
                    ),
                ]
            )
        )

        assertBitkeySnapshots(view: view)
    }

}

// MARK: -

private extension FormBodyModel {

    static func transactionDetailModel(
        isPending: Bool,
        isDelayed: Bool = false,
        transactionType: BitcoinTransactionTransactionType,
        isFeeBumpEnabled: Bool,
        content: [FormMainContentModel.DataList]
    ) -> FormBodyModel {
        let recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"
        let txStatusModel: TxStatusModel = if isPending {
            TxStatusModelPending(
                transactionType: transactionType,
                recipientAddress: recipientAddress,
                isLate: isDelayed
            )
        } else {
            TxStatusModelConfirmed(
                transactionType: transactionType,
                recipientAddress: recipientAddress
            )
        }

        return SnapshotTestModels.shared.CreateTransactionDetailModel(
            feeBumpEnabled: isFeeBumpEnabled,
            txStatusModel: txStatusModel,
            isLoading: false,
            onViewTransaction: {},
            onClose: {},
            onSpeedUpTransaction: {},
            content: content
        )
    }

}
