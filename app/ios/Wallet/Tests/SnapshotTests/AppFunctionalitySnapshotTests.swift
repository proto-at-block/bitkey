import Shared
import SnapshotTesting
import SwiftUI
import XCTest

@testable import Wallet

final class AppFunctionalitySnapshotTests: XCTestCase {

    func test_f8eUnreachable() {
        let view = FormView(
            viewModel: AppFunctionalityStatusBodyModelKt.AppFunctionalityStatusBodyModel(
                status: .init(cause: F8eUnreachable(lastReachableTime: .companion.DISTANT_PAST)), 
                cause: F8eUnreachable(lastReachableTime: .companion.DISTANT_PAST),
                dateFormatter: { _ in "9:14pm" },
                onClose: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }

    func test_internetUnreachable() {
        let view = FormView(
            viewModel: AppFunctionalityStatusBodyModelKt.AppFunctionalityStatusBodyModel(
                status: .init(cause: InternetUnreachable(lastReachableTime: .companion.DISTANT_PAST, lastElectrumSyncReachableTime: .companion.DISTANT_PAST)),
                cause: InternetUnreachable(lastReachableTime: .companion.DISTANT_PAST, lastElectrumSyncReachableTime: .companion.DISTANT_PAST),
                dateFormatter: { _ in "9:14pm" },
                onClose: {}
            )
        )

        assertBitkeySnapshots(view: view)
    }
}
