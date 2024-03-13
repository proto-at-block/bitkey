import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class RecoveryChannelsSetupFormScreenSnapshotTests: XCTestCase {
    func test_recovery_channels_setup_screen() {
        let view = FormView(
            viewModel: RecoveryChannelsSetupFormBodyModelKt.RecoveryChannelsSetupFormBodyModel(
                pushItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .none, onClick: {}),
                smsItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .none, onClick: {}),
                emailItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .none, onClick: {}),
                onBack: {},
                learnOnClick: {},
                continueOnClick: {},
                bottomSheetModel: nil,
                alertModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

     func test_recovery_channels_setup_screen_non_us_sim_us_number() {
        let view = FormView(
            viewModel: RecoveryChannelsSetupFormBodyModelKt.RecoveryChannelsSetupFormBodyModel(
                pushItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .none, onClick: {}),
                smsItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .notavailableinyourcountry, onClick: {}),
                emailItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .none, onClick: {}),
                onBack: {},
                learnOnClick: {},
                continueOnClick: {},
                bottomSheetModel: nil,
                alertModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }

    func test_recovery_channels_setup_screen_us_user() {
        let view = FormView(
            viewModel: RecoveryChannelsSetupFormBodyModelKt.RecoveryChannelsSetupFormBodyModel(
                pushItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .none, onClick: {}),
                smsItem: nil,
                emailItem: .init(state: .notcompleted, displayValue: "", uiErrorHint: .none, onClick: {}),
                onBack: {},
                learnOnClick: {},
                continueOnClick: {},
                bottomSheetModel: nil,
                alertModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_recovery_channels_setup_screen_all_complete() {
        let view = FormView(
            viewModel: RecoveryChannelsSetupFormBodyModelKt.RecoveryChannelsSetupFormBodyModel(
                pushItem: .init(state: .completed, displayValue: "", uiErrorHint: .none, onClick: {}),
                smsItem: .init(state: .completed, displayValue: "", uiErrorHint: .none, onClick: {}),
                emailItem: .init(state: .completed, displayValue: "", uiErrorHint: .none, onClick: {}),
                onBack: {},
                learnOnClick: {},
                continueOnClick: {},
                bottomSheetModel: nil,
                alertModel: nil
            ).body as! FormBodyModel
        )

        assertBitkeySnapshots(view: view)
    }
}
