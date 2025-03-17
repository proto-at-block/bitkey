import PhoneNumberKit
import Shared
import Testing

@testable import Wallet

struct PhoneNumberFormatterImplTests {

    private let phoneNumberKit = PhoneNumberKit()
    private let formatter = PhoneNumberFormatterImpl(
        phoneNumberLibBindings: PhoneNumberLibBindingsImpl()
    )

    @Test
    func formatsNumberWithoutPlusCharacter() {
        #expect(formatter.formatPartialPhoneNumber(number: "123456789") == "+1 (234) 567-89")
        #expect(formatter.formatPartialPhoneNumber(number: "44771234") == "+44 7712 34")
    }

    @Test
    func formatsValidNumber() {
        #expect(formatter.formatPartialPhoneNumber(number: "+33 6 12 345") == "+33 6 12 34 5")
    }

    @Test
    func returnsInvalidNumberWithoutFormat() {
        #expect(formatter.formatPartialPhoneNumber(number: "999999") == "999999")
    }
}
