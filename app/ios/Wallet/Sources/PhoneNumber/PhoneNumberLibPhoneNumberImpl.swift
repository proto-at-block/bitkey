import PhoneNumberKit
import protocol Shared.PhoneNumberLibPhoneNumber

final class PhoneNumberLibPhoneNumberImpl: PhoneNumberLibPhoneNumber {

    // MARK: - Internal Properties

    let libPhoneNumber: PhoneNumber

    // MARK: - Life Cycle

    init(libPhoneNumber: PhoneNumber) {
        self.libPhoneNumber = libPhoneNumber
    }

    // MARK: - PhoneNumberLibPhoneNumber

    var countryDialingCode: Int32 {
        return Int32(libPhoneNumber.countryCode)
    }

}
