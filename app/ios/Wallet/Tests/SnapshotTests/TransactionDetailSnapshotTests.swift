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
                isReceive: false,
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
                                sideText: "35,584 sats",
                                secondarySideText: nil,
                                showBottomDivider: false
                            ),
                            .init(
                                withTitle: "Network fees",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "5,526 sats",
                                secondarySideText: nil,
                                showBottomDivider: false
                            ),
                        ],
                        total: .init(
                            withTitle: "Total",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "41,110 sats",
                            sideTextType: .body2bold,
                            secondarySideText: "$10.00 at time sent",
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
                isReceive: true,
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
                        items: [
                            .init(
                                withTitle: "Amount received",
                                titleIcon: nil,
                                onTitle: {},
                                sideText: "35,584 sats",
                                secondarySideText: nil,
                                showBottomDivider: false
                            ),
                        ],
                        total: .init(
                            withTitle: "Total",
                            titleIcon: nil,
                            onTitle: {},
                            sideText: "35,584 sats",
                            sideTextType: .body2bold,
                            secondarySideText: "$10.00 at time confirmed",
                            showBottomDivider: false
                        ),
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
                isReceive: false,
                isFeeBumpEnabled: false,
                content: [
                    FormMainContentModel.DataList(
                        hero: nil,
                        items: [
                            .init(
                                withTitle: "Should arrive by",
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
                                withTitle: "Recipient receives",
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

    func test_transaction_detail_pending_late() {
        let view = FormView(
            viewModel: .transactionDetailModel(
                isPending: true,
                isDelayed: true,
                isReceive: false,
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

}

// MARK: -

private extension FormBodyModel {

    static func transactionDetailModel(
        isPending: Bool,
        isDelayed: Bool = false,
        isReceive: Bool,
        isFeeBumpEnabled: Bool,
        content: [FormMainContentModel.DataList]
    ) -> FormBodyModel {
        let recipientAddress = "bc1q xy2k gdyg jrsq tzq2 n0yr f249 3p83 kkfj hx0w lh"
        let txStatusModel: TxStatusModel = if isPending {
            TxStatusModelPending(
                isIncoming: isReceive,
                recipientAddress: recipientAddress,
                isLate: isDelayed
            )
        } else {
            TxStatusModelConfirmed(
                isIncoming: isReceive,
                recipientAddress: recipientAddress
            )
        }

        return TransactionDetailModelKt.TransactionDetailModel(
            feeBumpEnabled: isFeeBumpEnabled,
            txStatusModel: txStatusModel,
            isLoading: false,
            onLoaded: { _ in },
            onViewTransaction: {},
            onClose: {},
            onSpeedUpTransaction: {},
            content: content
        )
    }

}
