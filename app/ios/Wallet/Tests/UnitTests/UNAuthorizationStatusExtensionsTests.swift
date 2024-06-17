import Shared
import XCTest

class UNAuthorizationStatusExtensionsTests: XCTestCase {

    func test_authroized() {
        let status = UNAuthorizationStatusExtensions().convertToNotificationPermissionStatus(
            authorizationStatus: KotlinLong(
                integerLiteral: UNAuthorizationStatus.authorized
                    .rawValue
            )
        )
        XCTAssertEqual(status, PermissionStatus.authorized)
    }

    func test_denied() {
        let status = UNAuthorizationStatusExtensions().convertToNotificationPermissionStatus(
            authorizationStatus: KotlinLong(integerLiteral: UNAuthorizationStatus.denied.rawValue)
        )
        XCTAssertEqual(status, PermissionStatus.denied)
    }

    func test_not_determined() {
        let status = UNAuthorizationStatusExtensions().convertToNotificationPermissionStatus(
            authorizationStatus: KotlinLong(
                integerLiteral: UNAuthorizationStatus.notDetermined
                    .rawValue
            )
        )
        XCTAssertEqual(status, PermissionStatus.notdetermined)
    }

    func test_not_determined_ephemeral() {
        let status = UNAuthorizationStatusExtensions().convertToNotificationPermissionStatus(
            authorizationStatus: KotlinLong(
                integerLiteral: UNAuthorizationStatus.ephemeral
                    .rawValue
            )
        )
        XCTAssertEqual(status, PermissionStatus.notdetermined)
    }

    func test_not_determined_ephemeral_provisional() {
        let status = UNAuthorizationStatusExtensions().convertToNotificationPermissionStatus(
            authorizationStatus: KotlinLong(
                integerLiteral: UNAuthorizationStatus.provisional
                    .rawValue
            )
        )
        XCTAssertEqual(status, PermissionStatus.notdetermined)
    }

}
