import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class AppDelayNotifyInProgressSnapshotTests: XCTestCase {

    func test_app_delay_in_progress_zero_progress() {
        let view = AppDelayNotifyInProgressView(
            viewModel: .init(
                onStopRecovery: {},
                durationTitle: "12 hours",
                progress: 0,
                remainingDelayPeriod: 60
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_app_delay_in_progress_nonzero_progress() {
        let view = AppDelayNotifyInProgressView(
            viewModel: .init(
                onStopRecovery: {},
                durationTitle: "A few minutes",
                progress: 0.88,
                remainingDelayPeriod: 60
            )
        )

        assertBitkeySnapshots(view: view)
    }

}
