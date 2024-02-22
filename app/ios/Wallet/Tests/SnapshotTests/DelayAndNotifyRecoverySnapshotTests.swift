import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class DelayAndNotifyRecoverySnapshotTests: XCTestCase {

    func test_dn_new_key_ready_app() {
        let view = FormView(
            viewModel: RecoverYourMobileKeyBodyModelKt.DelayAndNotifyNewKeyReady(
                factorToRecover: .app,
                onStopRecovery: {},
                onCompleteRecovery: {}
            )
        )
        assertBitkeySnapshots(view: view, precision: 0.99)
    }

    func test_dn_new_key_ready_hardware() {
        let view = FormView(
            viewModel: RecoverYourMobileKeyBodyModelKt.DelayAndNotifyNewKeyReady(
                factorToRecover: .hardware,
                onStopRecovery: {},
                onCompleteRecovery: {}
            )
        )
        assertBitkeySnapshots(view: view, precision: 0.99)
    }

}
