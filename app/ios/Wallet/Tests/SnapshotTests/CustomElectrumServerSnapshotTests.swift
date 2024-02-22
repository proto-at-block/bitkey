import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class CustomElectrumServerSnapshotTests: XCTestCase {
    func test_custom_electrum_server_disabled() {
        let view = ElectrumServerSettingsView(
            viewModel: CustomElectrumServerBodyModel(
                onBack: {},
                switchIsChecked: false,
                electrumServerRow: nil,
                onSwitchCheckedChange: { _ in },
                disableAlertModel: nil
            )
        )

        assertBitkeySnapshots(view: view)
    }
    
    func test_custom_electrum_server_enabled() {
        let view = ElectrumServerSettingsView(
            viewModel: CustomElectrumServerBodyModel(
                onBack: {},
                switchIsChecked: true,
                electrumServerRow: .init(title: "Connected to:", sideText: "ssl://bitkey.mempool.space:50002", onClick: {}),
                onSwitchCheckedChange: { _ in },
                disableAlertModel: nil
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
