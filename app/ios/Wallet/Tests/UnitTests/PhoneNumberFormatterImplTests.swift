import PhoneNumberKit
import Shared
import XCTest

@testable import Wallet

class PhoneNumberFormatterImplTests: XCTestCase {

    private let phoneNumberKit = PhoneNumberKit()
    private let formatter = PhoneNumberFormatterImpl(
        phoneNumberLibBindings: PhoneNumberLibBindingsImpl()
    )

    func testFormatsNumberWithoutPlusCharacter() {
        XCTAssertEqual(formatter.formatPartialPhoneNumber(number: "123456789"), "+1 (234) 567-89")
        XCTAssertEqual(formatter.formatPartialPhoneNumber(number: "44771234"), "+44 7712 34")
    }

    func testFormatsValidNumber() {
        XCTAssertEqual(formatter.formatPartialPhoneNumber(number: "+33 6 12 345"), "+33 6 12 34 5")
    }

    func testReturnsInvalidNumberWithoutFormat() {
        XCTAssertEqual(formatter.formatPartialPhoneNumber(number: "999999"), "999999")
    }

}
