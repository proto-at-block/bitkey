import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class SplashSnapshotTests: XCTestCase {

    func test_splash() {
        let view = SplashScreenView(
            viewModel: SplashBodyModel(
                bitkeyWordMarkAnimationDelay: 0,
                bitkeyWordMarkAnimationDuration: 0,
                eventTrackerScreenInfo: nil
            )
        )
        assertBitkeySnapshots(view: view)
    }

}
