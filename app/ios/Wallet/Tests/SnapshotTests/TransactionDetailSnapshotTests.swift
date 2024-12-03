import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

// TODO(W-10113): Re-enable snapshot tests)
final class TransactionDetailSnapshotTests: XCTestCase {

    func skipped_test_transaction_detail_sent() {
        let view = ComposableRenderedModelView(
            model: FormBodyModel.transactionDetailModel(
                headline: "Transaction sent",
                subline: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                sublineTreatment: .mono,
                viewTransactionText: "View transaction",
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: false,
                content: [
                    TransactionDetailModelKt.completeTransactionStepper,
                    FormMainContentModel.Divider(),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Confirmed",
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
                                withTitle: "Amount",
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

    func skipped_test_transaction_detail_received() {
        let view = ComposableRenderedModelView(
            model: FormBodyModel.transactionDetailModel(
                headline: "Transaction received",
                subline: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                sublineTreatment: .mono,
                viewTransactionText: "View transaction",
                transactionType: BitcoinTransactionTransactionTypeIncoming(),
                isFeeBumpEnabled: false,
                content: [
                    TransactionDetailModelKt.completeTransactionStepper,
                    FormMainContentModel.Divider(),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [.init(
                            withTitle: "Confirmed",
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
                        items: [
                            .init(
                                withTitle: "Amount",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$10.00",
                                secondarySideText: "35,584 sats",
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

    func skipped_test_transaction_detail_utxoConsolidation() {
        let view = ComposableRenderedModelView(
            model: FormBodyModel.transactionDetailModel(
                headline: "UTXO Consolidation",
                subline: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                sublineTreatment: .mono,
                viewTransactionText: "View transaction",
                transactionType: BitcoinTransactionTransactionTypeUtxoConsolidation(),
                isFeeBumpEnabled: false,
                content: [
                    TransactionDetailModelKt.completeTransactionStepper,
                    FormMainContentModel.Divider(),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [.init(
                            withTitle: "Confirmed",
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

    func skipped_test_transaction_detail_pending() {
        let view = ComposableRenderedModelView(
            model: FormBodyModel.transactionDetailModel(
                headline: "Transaction pending",
                subline: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                sublineTreatment: .mono,
                viewTransactionText: "View transaction",
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: false,
                content: [
                    TransactionDetailModelKt.processingTransactionStepper,
                    FormMainContentModel.Divider(),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Arrival time",
                                titleIcon: nil,
                                onTitle: nil,
                                sideText: "Feb 1 at 5:44pm"
                            ),
                        ],
                        total: nil,
                        buttons: []
                    ),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Amount",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$200.00",
                                secondarySideText: nil,
                                showBottomDivider: false
                            ),
                            .init(
                                withTitle: "Network fees",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$1.36",
                                secondarySideText: nil,
                                showBottomDivider: false
                            ),
                        ],
                        total: .init(
                            withTitle: "Total",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "$201.36",
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

    func skipped_test_transaction_detail_pending_late() {
        let view = ComposableRenderedModelView(
            model: FormBodyModel.transactionDetailModel(
                headline: "Transaction delayed",
                subline: "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh",
                sublineTreatment: .mono,
                viewTransactionText: "View transaction",
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: true,
                content: [
                    TransactionDetailModelKt.processingTransactionStepper,
                    FormMainContentModel.Divider(),
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
                                    title: "Speed up transaction?",
                                    subtitle: "You can speed up this transaction by increasing the network fee.",
                                    iconButton: IconButtonModel(
                                        iconModel: IconModel(
                                            iconImage: .LocalImage(
                                                icon: .smalliconinformationfilled
                                            ),
                                            iconSize: .XSmall(),
                                            iconBackgroundType: IconBackgroundTypeCircle(
                                                circleSize: .XSmall(),
                                                color: .translucentblack
                                            ),
                                            iconAlignmentInBackground: .center,
                                            iconTint: nil,
                                            iconOpacity: 0.20,
                                            iconTopSpacing: nil,
                                            text: nil,
                                            badge: nil
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
                                withTitle: "Amount",
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

    func skipped_test_transaction_detail_pending_partnership() {
        let view = ComposableRenderedModelView(
            model: FormBodyModel.transactionDetailModel(
                headline: "Cash App transfer",
                subline: "Arrival times and fees are estimates. Confirm details through Cash App. ",
                sublineTreatment: .regular,
                viewTransactionText: "View in Cash App",
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: false,
                content: [
                    TransactionDetailModelKt.submittedTransactionStepper,
                    FormMainContentModel.Divider(),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Amount",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "$200.00",
                                secondarySideText: nil,
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

    func skipped_test_transaction_detail_confirmed_partnership() {
        let view = ComposableRenderedModelView(
            model: FormBodyModel.transactionDetailModel(
                headline: "Cash App sale",
                subline: "Arrival times and fees are estimates. Confirm details through Cash App.",
                sublineTreatment: .regular,
                viewTransactionText: "View in Cash App",
                transactionType: BitcoinTransactionTransactionTypeOutgoing(),
                isFeeBumpEnabled: false,
                content: [
                    TransactionDetailModelKt.completeTransactionStepper,
                    FormMainContentModel.Divider(),
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Confirmed",
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
                                withTitle: "Amount",
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

}

// MARK: -

private extension FormBodyModel {

    static func transactionDetailModel(
        headline: String,
        subline: String,
        sublineTreatment: FormHeaderModel.SublineTreatment,
        viewTransactionText: String,
        transactionType _: BitcoinTransactionTransactionType,
        isFeeBumpEnabled: Bool,
        content: [FormMainContentModel]
    ) -> FormBodyModel {
        let formHeaderModel: FormHeaderModel = .init(
            headline: headline,
            subline: subline,
            iconModel: IconModel(
                iconImage: .LocalImage(
                    icon: .bitcoin
                ),
                iconSize: .Avatar(),
                iconBackgroundType: IconBackgroundTypeTransient(),
                iconAlignmentInBackground: .center,
                iconTint: nil,
                iconOpacity: nil,
                iconTopSpacing: nil,
                text: nil,
                badge: nil
            ),
            sublineTreatment: sublineTreatment,
            alignment: .leading,
            customContent: nil
        )

        return SnapshotTestModels.shared.CreateTransactionDetailModel(
            feeBumpEnabled: isFeeBumpEnabled,
            formHeaderModel: formHeaderModel,
            isLoading: false,
            viewTransactionText: viewTransactionText,
            onViewTransaction: {},
            onClose: {},
            onSpeedUpTransaction: {},
            content: content
        )
    }

}
