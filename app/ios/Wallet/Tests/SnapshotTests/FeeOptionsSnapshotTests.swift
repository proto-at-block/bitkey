import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class FeeOptionsSnapshotTests: XCTestCase {
    
    func test_fee_options() {
        let view = FormView(
            viewModel: FeeSelectionScreenModelKt.FeeOptionsScreenModel(
                title: "Select a transfer speed",
                feeOptions: .init(options: [
                    .init(
                        optionName: "Priority",
                        transactionTime: "~10 min",
                        transactionFee: "$1.36 (4,475 sats)",
                        selected: false,
                        enabled: false,
                        infoText: "Not enough balance"
                    ),
                    .init(
                        optionName: "Standard",
                        transactionTime: "~30 min",
                        transactionFee: "$0.33 (1,086 sats)",
                        selected: true,
                        enabled: true,
                        infoText: nil
                    ),
                    .init(
                        optionName: "Slow",
                        transactionTime: "~1 hour",
                        transactionFee: "$1.95 (494 sats)",
                        selected: false,
                        enabled: true,
                        infoText: nil
                    )
                ]),
                primaryButton: .snapshotTest(text: "Continue"),
                onBack: {}
            )
        )
        
        assertBitkeySnapshots(view: view)
    }
    
}
