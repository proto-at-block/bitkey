import Foundation
import Shared
import UIKit
import XCTest

@testable import Wallet

class StateChangeHandlerTests: XCTestCase {

    let stateChangeHandler = StateChangeHandler(
        rootViewController: (UIViewController(), "string"),
        presentationStyle: .fullScreen
    )

    func test_dedupeQueuedActions() throws {
        let vc = UIViewController()

        let queuedActions: [StateChangeHandler.QueuedAction] = [
            .pushOrPop(vc: vc, stateKey: "key-1", animation: nil),
            .pushOrPop(vc: vc, stateKey: "key-2", animation: nil),
            .pushOrPop(vc: vc, stateKey: "key-1", animation: .fade),
            .clearStack,
            .pushOrPop(vc: vc, stateKey: "key-2", animation: nil),
            .pushOrPop(vc: vc, stateKey: "key-1", animation: .pushPop),
            .clearStack,
        ]

        let dedupedActions = stateChangeHandler.dedupeQueue(queueToDrain: queuedActions)
        let expectedActions: [StateChangeHandler.QueuedAction] = [
            .clearStack,
            .pushOrPop(vc: vc, stateKey: "key-2", animation: nil),
            .pushOrPop(vc: vc, stateKey: "key-1", animation: .pushPop),
            .clearStack,
        ]

        XCTAssertEqual(dedupedActions, expectedActions)
    }
}
