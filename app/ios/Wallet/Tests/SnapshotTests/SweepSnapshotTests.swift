import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class SweepSnapshotTests: XCTestCase {

    func test_sweep_funds_prompt_app() {
        let view = FormView(
            viewModel: SweepBodyModelsKt.sweepFundsPrompt(
                id: GeneralEventTrackerScreenId.debugMenu,
                recoveredFactor: .app,
                transferAmount: .init(primaryAmount: "$1000", secondaryAmount: "1,234 sats"),
                fee: .init(primaryAmount: "$1", secondaryAmount: "123 sats"),
                onShowNetworkFeesInfo: {},
                onCloseNetworkFeesInfo: {},
                showNetworkFeesInfoSheet: false,
                onBack: {},
                onHelpClick: {},
                onSubmit: {},
                presentationStyle: .fullscreen
            ).body as! FormBodyModel
        )
        assertBitkeySnapshots(view: view, precision: 0.99)
    }

    func test_sweep_funds_prompt_hardware() {
        let view = FormView(
            viewModel: SweepBodyModelsKt.sweepFundsPrompt(
                id: GeneralEventTrackerScreenId.debugMenu,
                recoveredFactor: .hardware,
                transferAmount: .init(primaryAmount: "$1000", secondaryAmount: "1,234 sats"),
                fee: .init(primaryAmount: "$1", secondaryAmount: "123 sats"),
                onShowNetworkFeesInfo: {},
                onCloseNetworkFeesInfo: {},
                showNetworkFeesInfoSheet: false,
                onBack: {},
                onHelpClick: {},
                onSubmit: {},
                presentationStyle: .fullscreen
            ).body as! FormBodyModel
        )
        assertBitkeySnapshots(view: view, precision: 0.99)
    }

    func test_zero_balance_prompt() {
        let view = FormView(
            viewModel: SweepBodyModelsKt.zeroBalancePrompt(
                id: GeneralEventTrackerScreenId.debugMenu,
                onDone: {},
                presentationStyle: .fullscreen
            ).body as! FormBodyModel
        )
        assertBitkeySnapshots(view: view, precision: 0.99)
    }

}
