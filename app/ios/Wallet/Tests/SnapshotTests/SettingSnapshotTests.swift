import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class SettingsSnapshotTests: XCTestCase {

    func test_settings() {
        let view = SettingsView(viewModel: .snapshotTest())
        assertBitkeySnapshots(view: view)
    }

    func test_settings_with_status_banner() {
        let view = SettingsView(viewModel: .snapshotTest())
        assertBitkeySnapshots(view: view, screenModel: .snapshotTest(statusBannerModel: .snapshotTest()))
    }

}


// MARK: -

extension SettingsBodyModel {
    static func snapshotTest() -> SettingsBodyModel {
        return .init(
            onBack: {},
            sectionModels: [
                .init(
                    sectionHeaderTitle: "General",
                    rowModels: [
                        .init(icon: .smalliconphone, title: "Mobile Pay", isDisabled: true, onClick: {}),
                        .init(icon: .smalliconcurrency, title: "Currency", isDisabled: false, onClick: {}),
                        .init(icon: .smalliconnotification, title: "Notifications", isDisabled: true, onClick: {}),
                    ]
                ),
                .init(
                    sectionHeaderTitle: "Security & Recovery",
                    rowModels: [
                        .init(icon: .smalliconshieldperson, title: "Trusted Contacts", isDisabled: true, onClick: {})
                    ]
                )
            ]
        )
    }
}
