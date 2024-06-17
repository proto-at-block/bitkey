import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class SplashLockSnapshotTests: XCTestCase {

    func test_splash_lock() {
        let view = SplashLockScreenView(
            viewModel: SplashLockModel(
                unlockButtonModel: ButtonModel(
                    text: "Unlock",
                    isEnabled: true,
                    isLoading: false,
                    leadingIcon: nil,
                    treatment: .translucent,
                    size: .footer,
                    testTag: nil,
                    onClick: StandardClick {}
                ),
                eventTrackerScreenInfo: nil,
                key: ""
            )
        )
        assertBitkeySnapshots(view: view)
    }

}
